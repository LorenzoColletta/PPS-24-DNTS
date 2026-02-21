package actors.gossip.consensus

import actors.discovery.DiscoveryProtocol.DiscoveryCommand
import actors.model.ModelActor.ModelCommand
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import config.AppConfig

/** Actor performing consensus calculation, how much the networks diverge */
object ConsensusActor:

  export ConsensusProtocol.*

  /**
   * Creates the initial behavior for the ConsensusActor.
   *
   * @param modelActor      Reference to the local [[ModelActor]]
   * @param discoveryActor  Reference to the [[DiscoveryActor]] for peer discovery.
   * @param config          Application global configuration.
   * @return
   */
  def apply(
    modelActor: ActorRef[ModelCommand],
    discoveryActor: ActorRef[DiscoveryCommand]
  )(using config: AppConfig): Behavior[ConsensusCommand] =

    Behaviors.setup: context =>
      Behaviors.withTimers: timers =>
        ConsensusBehavior(
          modelActor,
          discoveryActor,
          timers,
          config
        ).active(consensusRound  = None)
