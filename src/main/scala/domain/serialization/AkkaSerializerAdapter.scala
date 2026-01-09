package domain.serialization

import scala.util.{Failure, Success}
import akka.serialization.SerializerWithStringManifest

import domain.network.Network
import domain.network.Activations.given
import domain.serialization.NetworkSerializers.given
import domain.serialization.LinearAlgebraSerializers.given
import domain.serialization.Serializer as DomainSerializer

object AkkaSerializerAdapter:

  final val ManifestNetwork = "N"
  final val ManifestDataset = "D"

  private case class TypeBinding[T](
    manifest: String,
    clss: Class[T],
    serializer: DomainSerializer[T]
  )

  private val registry: List[TypeBinding[?]] = List(
    TypeBinding(ManifestNetwork, classOf[Network], summon[DomainSerializer[Network]])
  )

class AkkaSerializerAdapter extends SerializerWithStringManifest:
  import AkkaSerializerAdapter.registry
  import AkkaSerializerAdapter.TypeBinding

  override def identifier: Int = 99999

  private val manifestToBinding: Map[String, TypeBinding[?]] =
    registry.map(b => b.manifest -> b).toMap

  private val classToBinding: Map[Class[?], TypeBinding[?]] =
    registry.map(b => b.clss -> b).toMap


  override def manifest(o: AnyRef): String =
    classToBinding.get(o.getClass) match
      case Some(binding) => binding.manifest
      case None =>
        throw new IllegalArgumentException(s"Type not supported by AkkaSerializerAdapter: ${o.getClass.getName}")

  override def toBinary(o: AnyRef): Array[Byte] =
    classToBinding.get(o.getClass) match
      case Some(binding) =>
        binding.asInstanceOf[TypeBinding[AnyRef]].serializer.serialize(o)
      case None =>
        throw new IllegalArgumentException(s"Serializer not found for type: ${o.getClass.getName}")

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef =
    manifestToBinding.get(manifest) match
      case Some(binding) =>
        binding.serializer.deserialize(bytes) match
          case Success(obj) => obj.asInstanceOf[AnyRef]
          case Failure(ex)  =>
            throw new IllegalArgumentException(s"Deserialization failed for manifest '$manifest'", ex)
      case None =>
        throw new IllegalArgumentException(s"Unknown manifest: $manifest")
