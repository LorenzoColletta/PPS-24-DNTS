package domain.serialization

import actors.gossip.GossipActor.GossipCommand.{HandleDistributeDataset, HandleRemoteModel, ShareConfig}
import actors.gossip.GossipActor.ControlCommand
import actors.gossip.GossipProtocol.GossipCommand.HandleControlCommand
import actors.trainer.TrainerActor.TrainingConfig
import domain.data.{Label, LabeledPoint2D, Point2D}
import domain.network.Model

import scala.util.Try
import java.nio.ByteBuffer
import domain.serialization.LinearAlgebraSerializers.given
import domain.serialization.NetworkSerializers.given
import domain.serialization.ModelSerializers.given
import domain.network.Activations.given

import java.nio.charset.StandardCharsets

object GossipSerializers:

  given shareConfigSerializer(using
                              modelSer: Serializer[Model],
                              confSer: Serializer[TrainingConfig]
                             ): Serializer[ShareConfig] with

    def serialize(cmd: ShareConfig): Array[Byte] =
      val idBytes = cmd.seedID.getBytes(StandardCharsets.UTF_8)
      val mBytes = modelSer.serialize(cmd.model)
      val cBytes = confSer.serialize(cmd.config)

      val buffer = ByteBuffer.allocate(4 + idBytes.length + 4 + mBytes.length + 4 + cBytes.length)
      buffer.putInt(idBytes.length);
      buffer.put(idBytes)
      buffer.putInt(mBytes.length);
      buffer.put(mBytes)
      buffer.putInt(cBytes.length);
      buffer.put(cBytes)
      buffer.array()

    def deserialize(bytes: Array[Byte]): Try[ShareConfig] = Try {
      val buffer = ByteBuffer.wrap(bytes)

      val idLen = buffer.getInt;
      val idBytes = new Array[Byte](idLen);
      buffer.get(idBytes)
      val seedID = new String(idBytes, StandardCharsets.UTF_8)

      val mLen = buffer.getInt;
      val mBytes = new Array[Byte](mLen);
      buffer.get(mBytes)
      val model = modelSer.deserialize(mBytes).get

      val cLen = buffer.getInt;
      val cBytes = new Array[Byte](cLen);
      buffer.get(cBytes)
      val config = confSer.deserialize(cBytes).get

      ShareConfig(seedID, model, config)
    }

  given handleRemoteModelSerializer: Serializer[HandleRemoteModel] with
    override def serialize(t: HandleRemoteModel): Array[Byte] =
      summon[Serializer[Model]].serialize(t.remoteModel)

    override def deserialize(bytes: Array[Byte]): Try[HandleRemoteModel] =
      summon[Serializer[Model]].deserialize(bytes).map(model => HandleRemoteModel(model))

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

  given handleControlCommandSerializer(using controlSer: Serializer[ControlCommand]): Serializer[HandleControlCommand] with
    override def serialize(t: HandleControlCommand): Array[Byte] =
      controlSer.serialize(t.cmd)

    override def deserialize(bytes: Array[Byte]): Try[HandleControlCommand] =
      controlSer.deserialize(bytes).map(cmd => HandleControlCommand(cmd))