package actors.gossip

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import config.AppConfig
import actors.model.ModelActor.ModelCommand
import actors.monitor.MonitorActor.MonitorCommand
import actors.trainer.TrainerActor.TrainerCommand
import actors.cluster.ClusterCommand

/**
 * Actor responsible for propagating patterns and control signals
 * between nodes using the Gossip protocol.
 */
object GossipActor:

  export GossipProtocol.*

  /**
   * Creates the initial behavior for the GossipActor.
   *
   * @param modelActor     Reference to the local ModelActor.
   * @param monitorActor   Reference to the local MonitorActor.
   * @param trainerActor   Reference to the local TrainerActor.
   * @param clusterManager Reference to the ClusterManager for peer discovery.
   * @param config         Application global configuration.
   * @return A Behavior handling GossipCommand messages.
   */
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