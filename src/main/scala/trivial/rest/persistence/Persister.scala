package trivial.rest.persistence

import trivial.rest.{Failure, Resource}

import scala.reflect.ClassTag

trait Persister {
  def create[T <: Resource[T] : Manifest](resourceName: String, content: Seq[T]): Either[Failure, Int]

  def read[T <: Resource[T] : Manifest](resourceName: String, params: Map[String, String] = Map.empty): Either[Failure, Seq[T]]
  def read[T <: Resource[T] : Manifest](resourceName: String, id: String): Either[Failure, Seq[T]]

  def update[T <: Resource[T] : Manifest](resourceName: String, content: Seq[T]): Either[Failure, Int]

  def delete[T <: Resource[T] : Manifest](resourceName: String, predicate: T => Boolean): Either[Failure, Int]

  def delete[T <: Resource[T] : Manifest](resourceName: String, id: String): Either[Failure, Int] =
    delete[T](resourceName, (r: T) => r.id.contains(id))

  def nextSequenceId: String
  def formatSequenceId(id: Int): String

  // loadAll, backup, migrate data, save
  def migrate[T <: Resource[T] : ClassTag : Manifest](forward: (T) => T, oldApiPath: Option[String]): Either[Failure, Int]
}