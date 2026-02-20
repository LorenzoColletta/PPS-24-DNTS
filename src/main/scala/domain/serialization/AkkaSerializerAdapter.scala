package domain.serialization

import scala.util.{Failure, Success}
import akka.serialization.SerializerWithStringManifest
import akka.actor.ExtendedActorSystem
import akka.actor.typed.ActorRefResolver
import akka.actor.typed.scaladsl.adapter.*

import domain.network.Model
import domain.network.Activations.given
import domain.serialization.NetworkSerializers.given
import domain.serialization.LinearAlgebraSerializers.given
import domain.serialization.Serializer as DomainSerializer
import domain.serialization.GossipSerializers.given
import domain.serialization.ModelSerializers.given
import domain.serialization.ControlCommandSerializers.given
import domain.serialization.TrainingSerializers.given
import actors.gossip.GossipActor.GossipCommand
import actors.gossip.GossipActor.ControlCommand
import actors.gossip.GossipActor.GossipCommand.{HandleDistributeDataset, HandleRemoteModel}
import actors.gossip.GossipActor.GossipCommand.HandleControlCommand.given
import actors.gossip.consensus.ConsensusProtocol.*
import actors.gossip.configuration.ConfigurationProtocol

/**
 * Registry and configuration container for the [[AkkaSerializationAdapter]].
 * Defines the unique Manifest codes used to identify types.
 */
object AkkaSerializerAdapter:

  final val ManifestModel = "M"
  final val ManifestControl = "C"
  final val ManifestDistributeDataset = "D"
  final val ManifestRemoteModel = "R"
  final val ManifestShareConfig = "S"
  final val ManifestHandleControl = "HC"
  final val ManifestRequestInitialConfig = "RIC"
  final val ManifestRequestModelForConsensus = "RMF"
  final val ManifestReplyModelForConsensus = "RPLY"

  /**
   * Internal mapping connecting a specific Class type to its Manifest string
   * and its corresponding [[domain.serialization.Serializer]].
   */
  private case class TypeBinding[T](
    manifest: String,
    clss: Class[T],
    serializer: DomainSerializer[T]
  )


/**
 * Adapter class that integrates the custom [[domain.serialization.Serializer]] type classes
 * into the Akka Actor serialization infrastructure. .
 */
class AkkaSerializerAdapter(system: ExtendedActorSystem) extends SerializerWithStringManifest:
  import AkkaSerializerAdapter.*

  override def identifier: Int = 99999


  private given resolver: ActorRefResolver = ActorRefResolver(system.toTyped)

  /**
   * The static registry of supported types.
   */
  private val registry: List[TypeBinding[?]] = List(
    TypeBinding(ManifestModel, classOf[Model], summon[DomainSerializer[Model]]),
    TypeBinding(ManifestControl, classOf[ControlCommand], summon[DomainSerializer[ControlCommand]]),
    TypeBinding(ManifestDistributeDataset, classOf[HandleDistributeDataset], GossipSerializers.distributeDatasetSerializer),
    TypeBinding(ManifestRemoteModel, classOf[GossipCommand.HandleRemoteModel], GossipSerializers.handleRemoteModelSerializer),
    TypeBinding(ManifestShareConfig, classOf[ConfigurationProtocol.ShareConfig], GossipSerializers.shareConfigSerializer),
    TypeBinding(
      ManifestRequestInitialConfig,
      classOf[ConfigurationProtocol.RequestInitialConfig],
      GossipSerializers.requestInitialConfigSerializer(using resolver)
    ),
    TypeBinding(
      ManifestHandleControl,
      classOf[GossipCommand.HandleControlCommand],
      GossipSerializers.handleControlCommandSerializer
    ),
    TypeBinding(
      ManifestRequestModelForConsensus,
      classOf[RequestModelForConsensus],
      GossipSerializers.requestModelForConsensusSerializer(using resolver)
    ),
    TypeBinding(
      ManifestReplyModelForConsensus,
      classOf[ConsensusModelReply],
      GossipSerializers.consensusModelReplySerializer(using summon[DomainSerializer[Model]])
    )
  )

  private val manifestToBinding: Map[String, TypeBinding[?]] =
    registry.map(b => b.manifest -> b).toMap

  private val classToBinding: Map[Class[?], TypeBinding[?]] =
    registry.map(b => b.clss -> b).toMap


  /**
   * @param o The object to be serialized.
   * @return The "Manifest" (a short string code) associated with the object instance.
   * @throws IllegalArgumentException If the object type is not registered in this adapter.
   */
  override def manifest(o: AnyRef): String =
    classToBinding.get(o.getClass) match
      case Some(binding) => binding.manifest
      case None =>
        throw new IllegalArgumentException(s"Type not supported by AkkaSerializerAdapter: ${o.getClass.getName}")

  /**
   * Serializes the object into a byte array.
   * Delegates the logic to the [[domain.serialization.Serializer]] found in the registry.
   *
   * @param o The object to serialize.
   * @return The binary representation of the object.
   * @throws IllegalArgumentException If no serializer is found for the object's type.
   */
  override def toBinary(o: AnyRef): Array[Byte] =
    classToBinding.get(o.getClass) match
      case Some(binding) =>
        binding.asInstanceOf[TypeBinding[AnyRef]].serializer.serialize(o)
      case None =>
        throw new IllegalArgumentException(s"Serializer not found for type: ${o.getClass.getName}")

  /**
   * Deserializes a byte array back into a generic [[AnyRef]].
   *
   * @param bytes    The raw binary data received.
   * @param manifest The manifest string identifying the type of the object.
   * @return The reconstructed object.
   * @throws IllegalArgumentException If the manifest is unknown or deserialization fails.
   */
  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef =
    manifestToBinding.get(manifest) match
      case Some(binding) =>
        binding.serializer.deserialize(bytes) match
          case Success(obj) => obj.asInstanceOf[AnyRef]
          case Failure(ex)  =>
            throw new IllegalArgumentException(s"Deserialization failed for manifest '$manifest'", ex)
      case None =>
        throw new IllegalArgumentException(s"Unknown manifest: $manifest")
