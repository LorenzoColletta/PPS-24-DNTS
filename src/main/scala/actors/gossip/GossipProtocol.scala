package actors.gossip

import akka.actor.typed.ActorRef
import config.DatasetStrategyConfig
import domain.data.LabeledPoint2D
import domain.network.Model

object GossipProtocol:


  sealed trait GossipCommand extends Serializable

  sealed trait ControlCommand extends GossipCommand

  object ControlCommand:

    case object GlobalStart extends ControlCommand

    case object GlobalPause extends ControlCommand

    case object GlobalResume extends ControlCommand

    case object GlobalStop extends ControlCommand
  
  object GossipCommand:
    case object StartGossipTick extends GossipCommand
    case object TickGossip extends GossipCommand
    case object StopGossipTick extends GossipCommand
    final case class DistributeDataset(trainSet: List[LabeledPoint2D], testSet: List[LabeledPoint2D]) extends GossipCommand
    final case class WrappedDistributeDataset(peers: List[ActorRef[GossipCommand]], trainSet: List[LabeledPoint2D], testSet: List[LabeledPoint2D]) extends GossipCommand
    final case class HandleDistributeDataset(trainShard: List[LabeledPoint2D], testSet: List[LabeledPoint2D]) extends GossipCommand
    final case class WrappedPeers(peers: List[ActorRef[GossipCommand]]) extends GossipCommand
    final case class HandleRemoteModel(remoteModel: Model) extends GossipCommand
    final case class SendModelToPeer(model: Model, target: ActorRef[GossipCommand]) extends GossipCommand
    final case class SpreadCommand(cmd: ControlCommand) extends ControlCommand
    final case class WrappedSpreadCommand(peers: List[ActorRef[GossipCommand]], cmd: ControlCommand) extends ControlCommand
    final case class HandleControlCommand(cmd: ControlCommand) extends ControlCommand
