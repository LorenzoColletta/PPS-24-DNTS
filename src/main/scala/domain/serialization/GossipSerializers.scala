package domain.serialization

import actors.gossip.GossipProtocol.GossipCommand.{HandleDistributeDataset, HandleRemoteModel}
import domain.data.{Label, LabeledPoint2D, Point2D}
import domain.network.Model
import scala.util.Try
import java.nio.ByteBuffer

import domain.serialization.LinearAlgebraSerializers.given
import domain.serialization.NetworkSerializers.given
import domain.serialization.ModelSerializers.given
import domain.network.Activations.given

object GossipSerializers:

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