package trivial.rest.serialisation

import org.json4s.Formats
import org.json4s.native.Serialization
import trivial.rest.{Resource, Failure}

import scala.reflect.ClassTag

/**
 * Common errors:
 *
 * Problem: we expect a runtime type of T, but it is instead scala.runtime.Nothing$
 * Cause: an implicit call to formatsExcept[T : ClassTag] is not filling in the type parameter. Consider
 *   (a) calling formatsExcept[T : ClassTag] explicitly, or
 *   (b) bringing it in scope with implicit val formats = formatsExcept[T]
 */
trait Serialiser {
  def registerResource[T <: Resource[T] : ClassTag](allTheTs: Formats => Either[Failure, Seq[T]]): Unit
  def withDefaultFields[T : ClassTag](defaultObject: T): Serialiser
  implicit def formatsExcept[T : ClassTag]: Formats
  def deserialise[T : Manifest](body: String): Either[Failure, Seq[T]]

  // TODO - CAS - 24/06/15 - Combinify
  def serialise[T <: AnyRef : ClassTag](seqTs: Seq[T]): String = Serialization.write(seqTs)(formatsExcept[T])
  def serialise[T <: AnyRef : ClassTag](t: T): String = Serialization.write(t)(formatsExcept[T])
}