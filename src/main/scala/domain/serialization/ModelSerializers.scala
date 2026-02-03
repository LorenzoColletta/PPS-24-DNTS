package domain.serialization

import java.nio.ByteBuffer
import domain.network.{Model, Network, Feature}
import java.nio.charset.StandardCharsets
import scala.util.Try

/**
 * Binary serializers for the [[Model]] domain object and its components.
 * This object provides implicit strategies to convert high-level model structures
 * into byte arrays suitable for network transmission.
 */
object ModelSerializers:

  /**
   * Serializer for a list of [[Feature]].
   * Converts the features into a comma-separated string representation
   * encoded in UTF-8.
   */
  given featureListSerializer: Serializer[List[Feature]] with

    def serialize(features: List[Feature]): Array[Byte] =
      val names = features.map(_.toString).mkString(",")
      names.getBytes(StandardCharsets.UTF_8)

    def deserialize(bytes: Array[Byte]): Try[List[Feature]] = Try {
      val namesStr = new String(bytes, StandardCharsets.UTF_8)
      if (namesStr.isEmpty) Nil
      else
        namesStr.split(",").map {
          case "X" => Feature.X
          case "Y" => Feature.Y
          case "SquareX" => Feature.SquareX
          case "SquareY" => Feature.SquareY
          case "ProductXY" => Feature.ProductXY
          case "SinX" => Feature.SinX
          case "SinY" => Feature.SinY
          case name => throw new IllegalArgumentException(s"Feature sconosciuta: $name")
        }.toList
    }

  /**
   * Composite serializer for the [[Model]].
   * It orchestrates the serialization of the underlying Neural [[Network]]
   * and the associated input [[Feature]]s.
   *
   * @param netSer     The implicit [[Serializer]] for the [[Network]] topology.
   * @param featureSer The implicit [[Serializer]] for the list of [[Feature]].
   */
  given modelSerializer(
                         using
                         netSer: Serializer[Network],
                         featureSer: Serializer[List[Feature]]
                       ): Serializer[Model] with

    def serialize(model: Model): Array[Byte] =
      val netBytes = netSer.serialize(model.network)
      val featBytes = featureSer.serialize(model.features)

      val buffer = ByteBuffer.allocate(4 + netBytes.length + 4 + featBytes.length)

      buffer.putInt(netBytes.length)
      buffer.put(netBytes)

      buffer.putInt(featBytes.length)
      buffer.put(featBytes)

      buffer.array()

    def deserialize(bytes: Array[Byte]): Try[Model] = Try {
      val buffer = ByteBuffer.wrap(bytes)

      val netLen = buffer.getInt
      val netBytes = new Array[Byte](netLen)
      buffer.get(netBytes)
      val network = netSer.deserialize(netBytes).get

      val featLen = buffer.getInt
      val featBytes = new Array[Byte](featLen)
      buffer.get(featBytes)
      val features = featureSer.deserialize(featBytes).get

      Model(network, features)
    }
