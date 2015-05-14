package trivial.rest.serialisation

import org.json4s.{NoTypeHints, MappingException, Formats}
import org.json4s.native.Serialization
import trivial.rest.{Classy, Failure, Resource}

import scala.collection.mutable
import scala.reflect.ClassTag

class Json4sSerialiser extends Serialiser {

  private val resourceSerialisers = mutable.Map.empty[Class[_], ResourceSerialiser[_]]

  override def registerResource[T <: Resource[T] : ClassTag](allTheTs: Formats => Either[Failure, Seq[T]]) = {
    val serialiser = ResourceSerialiser[T](_.id.getOrElse(""), id => hunt(allTheTs(formatsExcept[T]), id))
    resourceSerialisers += Classy.runtimeClass[T] -> serialiser
  }

  // TODO - CAS - 11/05/15 - memoize
  override implicit def formatsExcept[T : ClassTag]: Formats =
    Serialization.formats(NoTypeHints) ++ (resourceSerialisers - Classy.runtimeClass[T]).values

  // TODO - CAS - 07/05/15 - Switch this to persister.getById, once we have /get/:id enabled
  def hunt[T <: Resource[T]](allTheTs: => Either[Failure, Seq[T]], id: String): Option[T] = {
    allTheTs match {
      case Right(seqTs) => seqTs.find(_.id == Some(id))
      case Left(failure) => None
    }
  }

  /*

  TODO - CAS - 01/05/15 - Map these to better error messages

        (1)
        // The extract[] method doesn't know the type of T, probably because it can't infer it.
        // Pass in the type explicitly. This is trying to create a Seq[Nothing], so it is trying
        // to build Nothings and failing. Fixed by calling loadAll[T](param) instead of loadAll(param)

        two: Left(Failure(500,Failed to deserialise into foo, due to: org.json4s.package$MappingException: Parsed JSON values do not match with class constructor
        args=
        arg types=
        constructor=public scala.runtime.Nothing$()))

        (2)
        // We have pulled an ID off disk, and we don't know how to map it to a thing.

        Left(Failure(500,Failed to deserialise into exchangerate, due to: org.json4s.package$MappingException: No usable value for currency
        No usable value for isoName
        Did not find value which can be converted into java.lang.String

  TODO - CAS - 01/05/15 - Try parsing the JSON AST, and showing that, for MappingException, which is about converting AST -> T
        */
  override def deserialise[T <: Resource[T] : Manifest](body: String): Either[Failure, Seq[T]] =
    try {
      Right(Serialization.read[Seq[T]](body))
    } catch {
      case m: MappingException => Left(Failure(500, SerialiserExceptionHelper.huntCause(m, Seq.empty[String])))
      case e: Exception => Left(Failure(500, s"THE ONE IN SERIALISER ===> Failed to deserialise into [T], due to: $e"))
    }
}