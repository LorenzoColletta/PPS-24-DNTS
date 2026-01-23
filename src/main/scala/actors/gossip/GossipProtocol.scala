package actors.gossip

import akka.actor.typed.ActorRef
import domain.network.Model

object GossipProtocol:


  sealed trait ControlCommand extends GossipCommand

  object ControlCommand:
    case object GlobalStart extends ControlCommand

    case object GlobalPause extends ControlCommand

    case object GlobalResume extends ControlCommand

    case object GlobalStop extends ControlCommand

  sealed trait GossipCommand extends Serializable

  object GossipCommand:
    case object TickGossip extends GossipCommand
    final case class HandleRemoteModel(remoteModel: Model) extends GossipCommand
    final case class SendModelToPeer(model: Model, target: ActorRef[GossipCommand]) extends GossipCommand
    final case class SpreadCommand(cmd: ControlCommand) extends ControlCommand
    final case class HandleControlCommand(cmd: ControlCommand) extends ControlCommand