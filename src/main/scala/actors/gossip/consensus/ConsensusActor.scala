package actors.gossip.consensus

import actors.discovery.DiscoveryProtocol.DiscoveryCommand
import actors.model.ModelActor.ModelCommand
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import config.AppConfig


object ConsensusActor:

  export ConsensusProtocol.*

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
