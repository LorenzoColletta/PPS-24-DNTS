package domain.serialization

import actors.gossip.GossipProtocol.ControlCommand
import domain.network.Model
import actors.trainer.TrainerActor.TrainingConfig
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import scala.util.Try


object ControlCommandSerializers:

  given controlCommandSerializer(using
                                 modelSer: Serializer[Model],
                                 configSer: Serializer[TrainingConfig]
                                ): Serializer[ControlCommand] with

    def serialize(cmd: ControlCommand): Array[Byte] =
      cmd match
        case ControlCommand.GlobalPause  => "GlobalPause".getBytes(StandardCharsets.UTF_8)
        case ControlCommand.GlobalResume => "GlobalResume".getBytes(StandardCharsets.UTF_8)
        case ControlCommand.GlobalStop   => "GlobalStop".getBytes(StandardCharsets.UTF_8)

        case ControlCommand.PrepareClient(seedID, model, tConfig) =>
          val idBytes = seedID.getBytes(StandardCharsets.UTF_8)
          val modelBytes = modelSer.serialize(model)
          val configBytes = configSer.serialize(tConfig)

          val capacity = 1 +
            4 + idBytes.length +
            4 + modelBytes.length +
            4 + configBytes.length

          val buffer = ByteBuffer.allocate(capacity)
          buffer.put(1.toByte)

          buffer.putInt(idBytes.length); buffer.put(idBytes)
          buffer.putInt(modelBytes.length); buffer.put(modelBytes)
          buffer.putInt(configBytes.length); buffer.put(configBytes)

          buffer.array()

        case other =>
          throw new IllegalArgumentException(s"Serialization not supported for ControlCommand: $other")

    def deserialize(bytes: Array[Byte]): Try[ControlCommand] = Try {
      
      if (bytes.length > 0 && bytes(0) == 1.toByte) {
        val buffer = ByteBuffer.wrap(bytes)
        buffer.get()

        val idLen = buffer.getInt
        val idBytes = new Array[Byte](idLen)
        buffer.get(idBytes)
        val seedID = new String(idBytes, StandardCharsets.UTF_8)

        val modLen = buffer.getInt
        val modBytes = new Array[Byte](modLen)
        buffer.get(modBytes)
        val model = modelSer.deserialize(modBytes).get

        val confLen = buffer.getInt
        val confBytes = new Array[Byte](confLen)
        buffer.get(confBytes)
        val config = configSer.deserialize(confBytes).get

        ControlCommand.PrepareClient(seedID, model, config)

      } else {

        val name = new String(bytes, StandardCharsets.UTF_8)
        name match
          case "GlobalPause"  => ControlCommand.GlobalPause
          case "GlobalResume" => ControlCommand.GlobalResume
          case "GlobalStop"   => ControlCommand.GlobalStop
          case _ => throw new IllegalArgumentException(s"Unknown ControlCommand: $name")
      }
    }