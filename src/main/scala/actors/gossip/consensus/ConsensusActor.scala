package actors.gossip.consensus

import actors.cluster.ClusterProtocol.ClusterMemberCommand
import actors.discovery.DiscoveryProtocol.{DiscoveryCommand, RegisterGossip}
import actors.gossip.GossipProtocol.GossipCommand
import actors.model.ModelActor.ModelCommand
import actors.root.RootActor.RootCommand
import actors.trainer.TrainerActor.TrainerCommand
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import config.AppConfig


object ConsensusActor:

  export ConsensusProtocol.*

  def apply(
             rootActor: ActorRef[RootCommand],
             modelActor: ActorRef[ModelCommand],
             trainerActor: ActorRef[TrainerCommand],
             discoveryActor: ActorRef[DiscoveryCommand]
           )(using config: AppConfig): Behavior[ConsensusCommand] =
    Behaviors.setup: context =>
      Behaviors.withTimers: timers =>
        ConsensusBehavior(
          rootActor,
          modelActor,
          trainerActor,
          discoveryActor,
          timers,
          config
        ).active(consensusRound  = None)