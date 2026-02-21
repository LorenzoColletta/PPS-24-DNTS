package domain.serialization

import actors.gossip.GossipActor.GossipCommand.HandleRemoteModel
import actors.gossip.configuration.ConfigurationProtocol.ShareConfig
import actors.gossip.GossipActor.ControlCommand
import actors.gossip.GossipProtocol.GossipCommand
import actors.gossip.GossipProtocol.GossipCommand.HandleControlCommand
import actors.gossip.consensus.ConsensusProtocol.{ConsensusModelReply, RequestModelForConsensus}
import actors.gossip.configuration.ConfigurationProtocol
import actors.gossip.dataset_distribution.DatasetDistributionProtocol.HandleDistributeDataset
import actors.trainer.TrainerActor.TrainingConfig
import akka.actor.typed.ActorRefResolver
import domain.data.{Label, LabeledPoint2D, Point2D}
import domain.network.Model

import scala.util.Try
import java.nio.ByteBuffer
import domain.serialization.LinearAlgebraSerializers.given
import domain.serialization.NetworkSerializers.given
import domain.serialization.ModelSerializers.given
import domain.network.Activations.given

import java.nio.charset.StandardCharsets

/**
 * Provides binary serializers for the Gossip protocol messages.
 * Uses ByteBuffer for manual binary encoding to ensure efficient network transmission.
 */
object GossipSerializers:

  /** Serializer for [[RequestModelForConsensus]]. */
  given requestModelForConsensusSerializer(using resolver: ActorRefResolver): Serializer[RequestModelForConsensus] with
    def serialize(cmd: RequestModelForConsensus): Array[Byte] =
      val refString = resolver.toSerializationFormat(cmd.replyTo)
      val refBytes = refString.getBytes(StandardCharsets.UTF_8)

      val buffer = ByteBuffer.allocate(4 + refBytes.length + 8)
      buffer.putInt(refBytes.length)
      buffer.put(refBytes)
      buffer.putLong(cmd.roundId)
      buffer.array()

    def deserialize(bytes: Array[Byte]): Try[RequestModelForConsensus] = Try {
      val buffer = ByteBuffer.wrap(bytes)
      val refLen = buffer.getInt
      val refBytes = new Array[Byte](refLen)
      buffer.get(refBytes)
      val refString = new String(refBytes, StandardCharsets.UTF_8)
      val roundId = buffer.getLong

      RequestModelForConsensus(resolver.resolveActorRef(refString), roundId)
    }

  /** Serializer for [[ConsensusModelReply]]. */
  given consensusModelReplySerializer(using modelSer: Serializer[Model]): Serializer[ConsensusModelReply] with
    def serialize(cmd: ConsensusModelReply): Array[Byte] =
      val mBytes = modelSer.serialize(cmd.model)
      val buffer = java.nio.ByteBuffer.allocate(8 + mBytes.length)
      buffer.putLong(cmd.roundId)
      buffer.put(mBytes)
      buffer.array()

    def deserialize(bytes: Array[Byte]): scala.util.Try[ConsensusModelReply] = scala.util.Try {
      val buffer = java.nio.ByteBuffer.wrap(bytes)
      val roundId = buffer.getLong
      val mBytes = new Array[Byte](buffer.remaining())
      buffer.get(mBytes)
      val model = modelSer.deserialize(mBytes).get
      ConsensusModelReply(model, roundId)
    }

  /** Serializer for [[ConfigurationProtocol.RequestInitialConfig]]. */
  given requestInitialConfigSerializer(using resolver: ActorRefResolver): Serializer[ConfigurationProtocol.RequestInitialConfig] with
    def serialize(cmd: ConfigurationProtocol.RequestInitialConfig): Array[Byte] =
      val refString = resolver.toSerializationFormat(cmd.replyTo)
      refString.getBytes(StandardCharsets.UTF_8)

    def deserialize(bytes: Array[Byte]): scala.util.Try[ConfigurationProtocol.RequestInitialConfig] = scala.util.Try {
      val refString = new String(bytes, StandardCharsets.UTF_8)
      ConfigurationProtocol.RequestInitialConfig(resolver.resolveActorRef(refString))
    }

  /** Serializer for [[ConfigurationProtocol.ShareConfig]]. */
  given shareConfigSerializer(using
                              modelSer: Serializer[Model],
                              confSer: Serializer[TrainingConfig]
                             ): Serializer[ConfigurationProtocol.ShareConfig] with // Usa il percorso completo per sicurezza

    def serialize(cmd: ConfigurationProtocol.ShareConfig): Array[Byte] =
      val idBytes = cmd.seedID.getBytes(StandardCharsets.UTF_8)
      val mBytes = modelSer.serialize(cmd.model)
      val cBytes = confSer.serialize(cmd.config)

      val buffer = ByteBuffer.allocate(4 + idBytes.length + 4 + mBytes.length + 4 + cBytes.length)
      buffer.putInt(idBytes.length)
      buffer.put(idBytes)
      buffer.putInt(mBytes.length)
      buffer.put(mBytes)
      buffer.putInt(cBytes.length)
      buffer.put(cBytes)
      buffer.array()

    def deserialize(bytes: Array[Byte]): Try[ConfigurationProtocol.ShareConfig] = Try {
      val buffer = ByteBuffer.wrap(bytes)

      val idLen = buffer.getInt
      val idBytes = new Array[Byte](idLen)
      buffer.get(idBytes)
      val seedID = new String(idBytes, StandardCharsets.UTF_8)

      val mLen = buffer.getInt
      val mBytes = new Array[Byte](mLen)
      buffer.get(mBytes)
      val model = modelSer.deserialize(mBytes).get

      val cLen = buffer.getInt
      val cBytes = new Array[Byte](cLen)
      buffer.get(cBytes)
      val config = confSer.deserialize(cBytes).get

      ConfigurationProtocol.ShareConfig(seedID, model, config)
    }

  /** Serializer for [[HandleRemoteModel]]. */
  given handleRemoteModelSerializer: Serializer[HandleRemoteModel] with
    override def serialize(t: HandleRemoteModel): Array[Byte] =
      summon[Serializer[Model]].serialize(t.remoteModel)

    override def deserialize(bytes: Array[Byte]): Try[HandleRemoteModel] =
      summon[Serializer[Model]].deserialize(bytes).map(model => HandleRemoteModel(model))

  /** Serializer for [[HandleDistributeDataset]]. */
  given distributeDatasetSerializer: Serializer[HandleDistributeDataset] with

    override def serialize(t: HandleDistributeDataset): Array[Byte] =
      val capacity = 4 + (t.trainShard.size * 20) + 4 + (t.testSet.size * 20)
      val buffer = ByteBuffer.allocate(capacity)

      //Train Shard
      buffer.putInt(t.trainShard.size)
      t.trainShard.foreach { p =>
        buffer.putDouble(p.point.x)
        buffer.putDouble(p.point.y)
        buffer.putInt(p.label.ordinal)
      }

      //Test Set
      buffer.putInt(t.testSet.size)
      t.testSet.foreach { p =>
        buffer.putDouble(p.point.x)
        buffer.putDouble(p.point.y)
        buffer.putInt(p.label.ordinal)
      }

      buffer.array()

    override def deserialize(bytes: Array[Byte]): Try[HandleDistributeDataset] = Try {
      val buffer = ByteBuffer.wrap(bytes)

      def readPoints(count: Int): List[LabeledPoint2D] =
        (0 until count).map { _ =>
          val x = buffer.getDouble
          val y = buffer.getDouble
          val label = Label.fromOrdinal(buffer.getInt)
          LabeledPoint2D(Point2D(x, y), label)
        }.toList

      val trainSize = buffer.getInt()
      val trainShard = readPoints(trainSize)

      val testSize = buffer.getInt()
      val testSet = readPoints(testSize)

      HandleDistributeDataset(trainShard, testSet)
    }

  /** Serializer for [[HandleControlCommand]]. */
  given handleControlCommandSerializer(using controlSer: Serializer[ControlCommand]): Serializer[HandleControlCommand] with
    override def serialize(t: HandleControlCommand): Array[Byte] =
      controlSer.serialize(t.cmd)

    override def deserialize(bytes: Array[Byte]): Try[HandleControlCommand] =
      controlSer.deserialize(bytes).map(cmd => HandleControlCommand(cmd))