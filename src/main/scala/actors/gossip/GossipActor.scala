package actors.gossip

import actors.gossip.GossipProtocol.*

import actors.ModelActor
import actors.ModelActor.ModelCommand
import actors.cluster.*
import actors.monitor.MonitorProtocol.MonitorCommand
import actors.trainer.TrainerActor.TrainerCommand

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors

import akka.actor.typed.scaladsl.TimerScheduler

import config.AppConfig
import domain.network.Model

import scala.util.Random
import scala.concurrent.duration.*

object GossipActor:

  def apply(
             modelActor: ActorRef[ModelCommand],
             monitorActor: ActorRef[MonitorCommand],
             trainerActor: ActorRef[TrainerCommand],
             clusterManager: ActorRef[ClusterCommand]
           )(using config: AppConfig): Behavior[GossipCommand] =
    Behaviors.setup: context =>
      Behaviors.withTimers: timers =>
        timers.startTimerWithFixedDelay(TickGossip,
                                        TickGossip,
                                        config.gossipInterval)
        context.log.info("GossipActor started: Peer-to-peer synchronization active.")
        active(modelActor, monitorActor, trainerActor, clusterManager)

  private def active(
                      modelActor: ActorRef[ModelCommand],
                      monitorActor: ActorRef[MonitorCommand],
                      trainerActor: ActorRef[TrainerCommand],
                      clusterManager: ActorRef[ClusterCommand]
                    ): Behavior[GossipCommand] =
    Behaviors.receive: (context, message) =>
      message match
        case TickGossip =>
          clusterManager ! NodesRefRequest(replyTo = )
          Behaviors.same
        case SendModelToPeer(model, target) =>
          target ! HandleRemoteModel(model)
          Behaviors.same
        case HandleRemoteModel(remoteModel) =>
          context.log.info("Gossip: Received remote model. Triggering synchronization.")
          modelActor ! ModelActor.ModelCommand.SyncModel(remoteModel)
          Behaviors.same

        //New node trigger Inizialize
        //GossipCommand.Inizialize
        //peer UP
        case SpreadCommand(cmd) =>
          context.log.info(s"Gossip: Broadcasting control signal $cmd to all known peers.")
          //peers.foreach(_ ! GossipCommand.HandleControlCommand(cmd))
          Behaviors.same

        case HandleControlCommand(cmd) =>
          context.log.info(s"Gossip: Executing remote control command: $cmd")
          cmd match
            case GlobalStart =>
              clusterManager ! StartSimulation
            case GlobalPause  =>
              monitorActor ! MonitorCommand.InternalPause
              trainerActor ! TrainerCommand.Pause
            case GlobalResume =>
              monitorActor ! MonitorCommand.InternalResume
              trainerActor ! TrainerCommand.Resume
            case GlobalStop   =>
              monitorActor ! MonitorCommand.InternalStop
              trainerActor ! TrainerCommand.Stop
          Behaviors.same

        case _ => Behaviors.unhandled
        
        //metrics consensus