package domain.serialization

import actors.gossip.GossipProtocol.ControlCommand
import java.nio.charset.StandardCharsets
import scala.util.Try

object ControlCommandSerializers:
  given controlCommandSerializer: Serializer[ControlCommand] with
    def serialize(cmd: ControlCommand): Array[Byte] =
      cmd.toString.getBytes(StandardCharsets.UTF_8)

    def deserialize(bytes: Array[Byte]): Try[ControlCommand] = Try {
      val name = new String(bytes, StandardCharsets.UTF_8)
      name match
        case "GlobalPause"  => ControlCommand.GlobalPause
        case "GlobalResume" => ControlCommand.GlobalResume
        case "GlobalStop"   => ControlCommand.GlobalStop
        case _ => throw new IllegalArgumentException(s"Unknown ControlCommand: $name")
    }