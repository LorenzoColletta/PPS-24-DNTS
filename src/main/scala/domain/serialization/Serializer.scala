package domain.serialization

import scala.util.Try

/**
 * Type class interface defining the contract for binary serialization.
 *
 * @tparam A The type of the object to be serialized.
 */
trait Serializer[A]:
  /** Converts the domain object into a compact byte array. */
  def serialize(value: A): Array[Byte]

  /**
   * Attempts to reconstruct the domain object from a byte array.
   * Returns a [[Try]] to safely handle data corruption or version mismatches.
   */
  def deserialize(bytes: Array[Byte]): Try[A]


/**
 * Interface for converting domain objects into human-readable text formats.
 */
trait Exporter[A]:
  /** Generates a structured JSON string representation of the object. */
  def jsonExport(value: A): String
