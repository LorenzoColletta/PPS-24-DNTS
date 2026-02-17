package actors.gossip

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import config.AppConfig
import actors.model.ModelActor.ModelCommand
import actors.trainer.TrainerActor.TrainerCommand
import actors.cluster.ClusterProtocol.ClusterMemberCommand
import actors.discovery.DiscoveryProtocol.{DiscoveryCommand, RegisterGossip}
import actors.root.RootActor.RootCommand

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
   * @param trainerActor   Reference to the local TrainerActor.
   * @param discoveryActor Reference to the DiscoveryActor for peer discovery.
   * @param config         Application global configuration.
   * @return A Behavior handling GossipCommand messages.
   */
  def apply(
    rootActor: ActorRef[RootCommand],
    modelActor: ActorRef[ModelCommand],
    trainerActor: ActorRef[TrainerCommand],
    discoveryActor: ActorRef[DiscoveryCommand]
  )(using config: AppConfig): Behavior[GossipCommand] =
    Behaviors.setup: context =>
      discoveryActor ! RegisterGossip(context.self)
      Behaviors.withTimers: timers =>
        GossipBehavior(
          rootActor,
          modelActor,
          trainerActor,
          discoveryActor,
          timers,
          config
        ).active(cachedConfig = None)
        