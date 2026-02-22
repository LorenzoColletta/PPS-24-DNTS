package domain.serialization

import actors.trainer.TrainerActor.TrainingConfig
import domain.network.{Feature, HyperParams, Regularization}
import domain.network.Regularization.{ElasticNet, L1, L2, None as RegNone}

import java.nio.ByteBuffer
import scala.util.Try

object TrainingSerializers:

  given regularizationSerializer: Serializer[Regularization] with
    def serialize(reg: Regularization): Array[Byte] =
      val buffer = ByteBuffer.allocate(24)
      reg match
        case RegNone =>
          buffer.putInt(0)
        case L2(l) =>
          buffer.putInt(1); buffer.putDouble(l)
        case L1(l) =>
          buffer.putInt(2); buffer.putDouble(l)
        case ElasticNet(l1, l2) =>
          buffer.putInt(3); buffer.putDouble(l1); buffer.putDouble(l2)

      buffer.array().take(buffer.position())

    def deserialize(bytes: Array[Byte]): Try[Regularization] = Try {
      val buffer = ByteBuffer.wrap(bytes)
      buffer.getInt match
        case 0 => RegNone
        case 1 => L2(buffer.getDouble)
        case 2 => L1(buffer.getDouble)
        case 3 => ElasticNet(buffer.getDouble, buffer.getDouble)
        case _ => throw new IllegalArgumentException("Unknown Regularization type")
    }

  given trainingConfigSerializer(using
                                 featSer: Serializer[List[Feature]],
                                 regSer: Serializer[Regularization]
                                ): Serializer[TrainingConfig] with

    def serialize(conf: TrainingConfig): Array[Byte] =
      val featBytes = featSer.serialize(conf.features)
      val regBytes = regSer.serialize(conf.hp.regularization)

      val totalSize =
        4 + featBytes.length +
          8 +
          4 + regBytes.length +
          4 +
          4 +
          8 + 1

      val buffer = ByteBuffer.allocate(totalSize)

      buffer.putInt(featBytes.length)
      buffer.put(featBytes)

      buffer.putDouble(conf.hp.learningRate)
      buffer.putInt(regBytes.length)
      buffer.put(regBytes)

      buffer.putInt(conf.epochs)
      buffer.putInt(conf.batchSize)

      conf.seed match
        case Some(s) => buffer.put(1.toByte); buffer.putLong(s)
        case None    => buffer.put(0.toByte); buffer.putLong(0L) // Padding

      buffer.array()

    def deserialize(bytes: Array[Byte]): Try[TrainingConfig] = Try {
      val buffer = ByteBuffer.wrap(bytes)

      val featLen = buffer.getInt
      val featBytes = new Array[Byte](featLen)
      buffer.get(featBytes)
      val features = featSer.deserialize(featBytes).get

      val lr = buffer.getDouble
      val regLen = buffer.getInt
      val regBytes = new Array[Byte](regLen)
      buffer.get(regBytes)
      val reg = regSer.deserialize(regBytes).get

      val hp = HyperParams(lr, reg)

      val epochs = buffer.getInt
      val batchSize = buffer.getInt

      val hasSeed = buffer.get() == 1.toByte
      val seedVal = buffer.getLong
      val seed = if (hasSeed) Some(seedVal) else None

      TrainingConfig(Nil, Nil, features, hp, epochs, batchSize, seed)
    }
