package actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import domain.network.Model
import actors.ModelActor.ModelCommand
import domain.data.LabeledPoint2D

//inserire timer


object GossipActor:
  enum GossipCommand:
    case ShareModel(model: Model)
    case RemoteUpdate(model: Model)
    case DistributeData(trainSet: List[LabeledPoint2D], testSet: List[LabeledPoint2D])
    case SpreadCommand(command: GossipCommand)
    case GlobalStop, GlobalPause, GlobalResume

  def apply(modelActor: ActorRef[ModelCommand]): Behavior[GossipCommand] =
    Behaviors.receive: (context, message) =>
      message match
        case GossipCommand.ShareModel(localModel) =>
          context.log.info("GossipActor : Broadcasting local model to P2P cluster...")
          Behaviors.same

        case GossipCommand.RemoteUpdate(remoteModel) =>
          context.log.info("GossipActor: Received updated model from remote node")
          //modelActor ! ModelCommand.SyncGossipProtocol(remoteModel)
          Behaviors.same
