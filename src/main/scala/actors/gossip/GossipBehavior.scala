package actors.gossip

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import actors.gossip.GossipProtocol.*
import actors.ModelActor
import actors.ModelActor.ModelCommand.Initialize
import actors.cluster.{ClusterCommand, NodesRefRequest, StartSimulation}
import actors.trainer.TrainerActor.TrainerCommand
import actors.monitor.MonitorProtocol.MonitorCommand
import domain.network.Model
import scala.util.Random

private[gossip] class GossipBehavior(
                                      context: ActorContext[GossipCommand],
                                      modelActor: ActorRef[ModelActor.ModelCommand],
                                      monitorActor: ActorRef[MonitorCommand],
                                      trainerActor: ActorRef[TrainerCommand],
                                      clusterManager: ActorRef[ClusterCommand]
                                    ):

  def active(): Behavior[GossipCommand] =
    Behaviors.receive: (context, message) =>
      message match
        case GossipCommand.TickGossip =>
          clusterManager ! NodesRefRequest(context.messageAdapter { peers =>
            val potentialPeers = peers.filter(_ != context.self).toList
            if potentialPeers.nonEmpty then
              val target = potentialPeers(Random.nextInt(potentialPeers.size))
              modelActor ! ModelActor.ModelCommand.GetModel(
                context.messageAdapter(model => GossipCommand.SendModelToPeer(model, target))
              )
            GossipCommand.TickGossip
          })
          Behaviors.same
        case GossipCommand.SendModelToPeer(model, target) =>
          context.log.info(s"Gossip: Sending local model weights to peer")
          target ! GossipCommand.HandleRemoteModel(model)
          Behaviors.same
        case GossipCommand.HandleRemoteModel(remoteModel) =>
          context.log.info("Remote model received: initiating local synchronization.")
          modelActor ! ModelActor.ModelCommand.SyncModel(remoteModel)
          Behaviors.same
        case GossipCommand.HandleControlCommand(cmd) =>
          context.log.info(s"Executing remote control command: $cmd")
          cmd match
            case ControlCommand.GlobalStart =>
              clusterManager ! StartSimulation
              monitorActor ! MonitorCommand.StartSimulation
              //modelActor ! ModelCommand
            case ControlCommand.GlobalPause =>
              monitorActor ! MonitorCommand.InternalPause
              trainerActor ! TrainerCommand.Pause
            case ControlCommand.GlobalResume =>
              monitorActor ! MonitorCommand.InternalResume
              trainerActor ! TrainerCommand.Resume
            case ControlCommand.GlobalStop =>
              monitorActor ! MonitorCommand.InternalStop
              trainerActor ! TrainerCommand.Stop
          Behaviors.same

        case _ =>
          context.log.warn("Received unhandled gossip message.")
          Behaviors.unhandled