package actors.gossip

import actors.ModelActor
import actors.ModelActor.ModelCommand
import actors.gossip.GossipProtocol.ControlCommand
import actors.gossip.GossipProtocol.GossipCommand
import actors.monitor.MonitorProtocol.MonitorCommand
import actors.trainer.TrainerActor.TrainerCommand
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import config.AppConfig
import domain.network.Model
import scala.util.Random

object GossipActor:

  def apply(
             modelActor: ActorRef[ModelCommand],
             monitorActor: ActorRef[MonitorCommand],
             trainerActor: ActorRef[TrainerCommand]
           )(using config: AppConfig): Behavior[GossipCommand] =
    Behaviors.setup: context =>
      Behaviors.withTimers: timers =>
        timers.startTimerWithFixedDelay(GossipCommand.TickGossip, GossipCommand.TickGossip, config.gossipInterval)
        context.log.info("GossipActor started: Peer-to-peer synchronization active.")
        active(modelActor, monitorActor, trainerActor, Set.empty)

  private def active(
                      modelActor: ActorRef[ModelCommand],
                      monitorActor: ActorRef[MonitorCommand],
                      trainerActor: ActorRef[TrainerCommand],
                      peers: Set[ActorRef[GossipCommand]]
                    ): Behavior[GossipCommand] =
    Behaviors.receive: (context, message) =>
      message match
        case GossipCommand.TickGossip =>
          val remotePeers = (peers - context.self).toList

          if remotePeers.nonEmpty then
            val target = Random.shuffle(remotePeers).head
            modelActor ! ModelActor.ModelCommand.GetModel(
              context.messageAdapter(model =>
                target ! GossipCommand.HandleRemoteModel(model)
                GossipCommand.UpdatePeers(peers)
              )
            )
          Behaviors.same

        case GossipCommand.HandleRemoteModel(remoteModel) =>
          context.log.info("Gossip: Received remote model. Triggering synchronization.")
          modelActor ! ModelActor.ModelCommand.SyncModel(remoteModel)
          Behaviors.same

        case GossipCommand.UpdatePeers(newPeers) =>
          context.log.debug(s"Gossip: Peer list updated. Knowledge base: ${newPeers.size} nodes.")
          active(modelActor, monitorActor, trainerActor, newPeers)

        case GossipCommand.SpreadCommand(cmd) =>
          context.log.info(s"Gossip: Broadcasting control signal $cmd to all known peers.")
          peers.foreach(_ ! GossipCommand.HandleControlCommand(cmd))
          Behaviors.same

        case GossipCommand.HandleControlCommand(cmd) =>
          context.log.info(s"Gossip: Executing remote control command: $cmd")
          cmd match
            case ControlCommand.GlobalPause  => 
              monitorActor ! MonitorCommand.InternalPause
              trainerActor ! TrainerCommand.Pause
            case ControlCommand.GlobalResume => 
              monitorActor ! MonitorCommand.InternalResume
              trainerActor ! TrainerCommand.Resume
            case ControlCommand.GlobalStop   => 
              monitorActor ! MonitorCommand.InternalStop
              trainerActor ! TrainerCommand.Stop
          Behaviors.same

        case _ => Behaviors.unhandled