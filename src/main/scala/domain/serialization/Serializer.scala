package domain.serialization

import scala.util.Try

trait Serializer[A]:
  def serialize(value: A): Array[Byte]
  def deserialize(bytes: Array[Byte]): Try[A]

trait Exporter[A]:
  def jsonExport(value: A): String
