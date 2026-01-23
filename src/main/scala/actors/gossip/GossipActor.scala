package actors.gossip

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import config.AppConfig
import actors.ModelActor.ModelCommand
import actors.monitor.MonitorProtocol.MonitorCommand
import actors.trainer.TrainerActor.TrainerCommand
import actors.cluster.ClusterCommand

object GossipActor:

  export GossipProtocol.*

  def apply(
             modelActor: ActorRef[ModelCommand],
             monitorActor: ActorRef[MonitorCommand],
             trainerActor: ActorRef[TrainerCommand],
             clusterManager: ActorRef[ClusterCommand]
           )(using config: AppConfig): Behavior[GossipCommand] =
    Behaviors.setup: context =>
      Behaviors.withTimers: timers =>
        timers.startTimerWithFixedDelay(
          GossipCommand.TickGossip,
          GossipCommand.TickGossip,
          config.gossipInterval
        )

        GossipBehavior(
          context,
          modelActor,
          monitorActor,
          trainerActor,
          clusterManager
        ).active()