package actors.gossip

import akka.actor.typed.ActorRef
import domain.network.Model
import akka.actor.typed.receptionist.ServiceKey

object GossipProtocol:

  sealed trait Message extends Serializable

  sealed trait GossipCommand extends Message

  case object TickGossip extends GossipCommand

  final case class HandleRemoteModel(remoteModel: Model) extends GossipCommand

  final case class SendModelToPeer(model: Model, target: ActorRef[GossipCommand]) extends GossipCommand


  sealed trait ControlCommand extends Message with GossipCommand

  case object GlobalStart  extends ControlCommand
  case object GlobalPause  extends ControlCommand
  case object GlobalResume extends ControlCommand
  case object GlobalStop   extends ControlCommand

  final case class SpreadCommand(cmd: ControlCommand) extends GossipCommand