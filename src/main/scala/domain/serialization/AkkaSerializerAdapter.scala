package domain.serialization

import scala.util.{Failure, Success}
import akka.serialization.Serializer

import domain.network.Network
import domain.network.Activations.given
import domain.serialization.NetworkSerializers.given
import domain.serialization.LinearAlgebraSerializers.given
import domain.serialization.Serializer as DomainSerializer

class AkkaSerializerAdapter extends Serializer {

  override def identifier: Int = 99999

  override def includeManifest: Boolean = true

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case n: Network =>
      summon[DomainSerializer[Network]].serialize(n)

    case _ =>
      throw new IllegalArgumentException(s"AkkaSerializerAdapter cannot serialize object of type: ${o.getClass.getName}")
  }

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[?]]): AnyRef = {
    manifest match {
      case Some(c) if c == classOf[Network] =>
        deserializeNetwork(bytes)

      case Some(c) =>
        throw new IllegalArgumentException(s"Unknown manifest class: ${c.getName}")

      case None =>
        throw new IllegalArgumentException("Manifest is required but missing")
    }
  }

  private def deserializeNetwork(bytes: Array[Byte]): Network = {
    summon[DomainSerializer[Network]].deserialize(bytes) match {
      case Success(net) => net
      case Failure(ex)  => throw new IllegalArgumentException("Failed to deserialize Network", ex)
    }
  }
}
