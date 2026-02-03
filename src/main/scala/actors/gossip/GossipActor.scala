package actors.gossip

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import config.AppConfig
import actors.model.ModelActor.ModelCommand
import actors.monitor.MonitorActor.MonitorCommand
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
    Behaviors.setup: _ =>
      Behaviors.withTimers: timers =>
        GossipBehavior(
          modelActor,
          monitorActor,
          trainerActor,
          clusterManager,
          timers,
          config
        ).active()