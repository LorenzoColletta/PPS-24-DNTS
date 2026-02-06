package actors.gossip

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import config.AppConfig
import actors.model.ModelActor.ModelCommand
import actors.monitor.MonitorActor.MonitorCommand
import actors.trainer.TrainerActor.TrainerCommand
import actors.cluster.ClusterProtocol.ClusterMemberCommand
import actors.discovery.DiscoveryProtocol.DiscoveryCommand

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
   * @param clusterManager Reference to the local ClusterManager.
   * @param discoveryActor Reference to the DiscoveryActor for peer discovery.
   * @param config         Application global configuration.
   * @return A Behavior handling GossipCommand messages.
   */
  def apply(
             modelActor: ActorRef[ModelCommand],
             monitorActor: ActorRef[MonitorCommand],
             trainerActor: ActorRef[TrainerCommand],
             clusterManager: ActorRef[ClusterMemberCommand],
             discoveryActor: ActorRef[DiscoveryCommand]
  )(using config: AppConfig): Behavior[GossipCommand] =
    Behaviors.setup: _ =>
      Behaviors.withTimers: timers =>
        GossipBehavior(
          modelActor,
          monitorActor,
          trainerActor,
          clusterManager,
          discoveryActor,
          timers,
          config
        ).active()