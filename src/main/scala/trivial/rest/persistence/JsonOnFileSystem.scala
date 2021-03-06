package trivial.rest.persistence

import org.apache.commons.io.FileUtils._
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter}
import org.joda.time.{DateTime, DateTimeZone}
import org.json4s.JsonAST._
import org.json4s.native.Serialization
import org.json4s.{JValue, JsonAST}
import trivial.rest.caching.Memo
import trivial.rest.serialisation.Serialiser
import trivial.rest.{Failure, Resource}

import scala.reflect.ClassTag
import scala.reflect.io.{Directory, File}

class JsonOnFileSystem(docRoot: Directory, serialiser: Serialiser) extends Persister with Memo {

  def delete[T <: Resource[T] : Manifest](resourceName: String, predicate: T => Boolean): Either[Failure, Int] =
    for {
      preExistingResources <- read[T](resourceName).right
    } yield {
      val withoutDeletedResource = preExistingResources.filterNot(predicate)
      saveOnly(resourceName, withoutDeletedResource)
      preExistingResources.size - withoutDeletedResource.size
    }

  // TODO - CAS - 03/07/15 - Look for a better way to do this, e.g. loadAll, modify the relevant T, then save all. At least that is atomic.
  override def update[T <: Resource[T] : Manifest](resourceName: String, content: Seq[T]): Either[Failure, Int] = {
    // TODO - CAS - 03/07/15 - Yes, massive hole here: if delete succeeds but create fails, we have lost the data
    content.map(t => delete[T](resourceName, (r: T) => r.id == t.id))
    // TODO - CAS - 03/07/15 - We should not try to re-create if the delete failed.
    create(resourceName, content)
  }

  override def read[T <: Resource[T] : Manifest](resourceName: String, id: String) = read[T](resourceName, Map("id" -> id))

  override def read[T <: Resource[T] : Manifest](resourceName: String, params: Map[String, String]) =
    if (params.nonEmpty) loadOnly[T](resourceName, params)
    else loadAll[T](resourceName)

  private def loadOnly[T : Manifest](resourceName: String, params: Map[String, String]): Either[Failure, Seq[T]] = {
    // TODO - CAS - 26/06/15 - Push this type of filtering into Serialiser, with a default null implementation. The persister does not have to use it (but it can if it likes).
    // TODO - CAS - 26/06/15 - Handle params which do not exist as fields in the Resource.
    // TODO - CAS - 26/06/15 - We need to match the type of each field, for example numbers are not quoted Strings
    def checkAll(fieldConstraints: List[(String, JsonAST.JValue)], fieldsInT: List[(String, JsonAST.JValue)]) =
      !fieldConstraints.exists(field => !fieldsInT.contains(field))

    val constraints: List[(String, JString)] = params.map(param => param._1 -> JString(param._2)).toList

    val ast: JValue = loadAstFromDisk(resourceName).asInstanceOf[JValue]

    val matchingResources: List[JObject] = for {
      JArray(resources) <- ast
      JObject(resource) <- resources if checkAll(constraints, resource)
    } yield JObject(resource)

    serialiser.deserialiseToType[T](JArray(matchingResources).asInstanceOf[serialiser.JsonRepresentation])
  }

  private def loadAll[T : Manifest](resourceName: String): Either[Failure, Seq[T]] =
    memo(resourceName) { actuallyLoadAll[T] }(resourceName)

  private def actuallyLoadAll[T : Manifest](resourceName: String): Either[Failure, Seq[T]] =
    serialiser.deserialiseToType[T](loadAstFromDisk(resourceName))

  private def loadAstFromDisk(resourceName: String): serialiser.JsonRepresentation =
    if (hasLocalFile(fileFor(resourceName)))
      serialiser.deserialiseToJson(fromDisk(resourceName))
    else
      serialiser.emptyJson

  override def migrate[T <: Resource[T] : ClassTag : Manifest](forward: (T) => T, oldResourceName: Option[String]): Either[Failure, Int] = {
    val targetName = Resource.name[T]
    val sourceName = oldResourceName.getOrElse(targetName)
    val backupName = s"$sourceName-${stamp()}"

    FileSystem.move(assuredFile(docRoot, sourceName), fileFor(backupName))

    if (fileFor(backupName).slurp().trim.isEmpty)
      Right(0)
    else
      read[T](backupName).right.map(_.map(forward)).right.flatMap(seqTs => create(targetName, seqTs))
  }

  private lazy val timestampFormat: DateTimeFormatter = DateTimeFormat.forPattern("yyyyddMMHHmmssSSS")
  private def stamp(): String = DateTime.now(DateTimeZone.UTC).toString(timestampFormat)

  // TODO - CAS - 14/09/15 - Check for duplicates - a persistence layer concern
  override def create[T <: Resource[T] : Manifest](resourceName: String, newItems: Seq[T]) =
    read[T](resourceName).right.map { previousItems =>
      saveOnly(resourceName, previousItems ++ newItems)
      newItems.size
    }

  private def saveOnly[T <: Resource[T] : Manifest](resourceName: String, toSave: Seq[T]): Unit = {
    // TODO - CAS - 09/06/15 - Change this to call serialiser.serialise(previousItems ++ newItems)
    assuredFile(docRoot, resourceName).writeAll(Serialization.write(toSave)(serialiser.formatsExcept[T]))
    unMemo(resourceName)
  }

  // TODO - CAS - 21/04/15 - Consider Scala async to make this write-behind: https://github.com/scala/async
  override def nextSequenceId: String = {
    val targetFile = assuredFile(docRoot, "_sequence", "0")
    val previous = targetFile.slurp().toInt
    val next = previous + 1
    targetFile.writeAll(s"$next")
    formatSequenceId(next)
  }

  override def formatSequenceId(id: Int): String = f"$id%07d"

  // TODO - CAS - 14/05/15 - Extract FS methods to a separate FileSystem dependency?

  def assuredFile(docRoot: Directory, targetResourceName: String, defaultContents: String = ""): File = {
    if (!docRoot.exists) docRoot.createDirectory()
    val targetFile = fileFor(targetResourceName)
    if (!targetFile.exists) {
      targetFile.createFile()
      targetFile.writeAll(defaultContents)
    }
    targetFile
  }

  private def fromDisk[T <: Resource[T]](resourceName: String): String =
    readFileToString(fileFor(resourceName).jfile)

  def fileFor(resourceName: String): File = File(docRoot / s"$resourceName.json")

  def hasLocalFile(file: File): Boolean = {
    if(file.toString().contains(".."))   return false
    if(!file.exists || file.isDirectory) return false
    if(!file.canRead)                    return false

    true
  }
}