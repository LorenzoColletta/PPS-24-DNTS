package actors.gossip.configuration

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import config.AppConfig
import actors.model.ModelActor.ModelCommand
import actors.trainer.TrainerActor.TrainerCommand
import actors.cluster.ClusterProtocol.ClusterMemberCommand
import actors.discovery.DiscoveryProtocol.{DiscoveryCommand, RegisterGossip}
import actors.gossip.GossipProtocol.GossipCommand
import actors.root.RootActor.RootCommand


object ConfigurationActor:

    export ConfigurationProtocol.*

    def apply(
               discoveryActor: ActorRef[DiscoveryCommand]
             )(using config: AppConfig): Behavior[ConfigurationProtocol.ConfigurationCommand] =
      Behaviors.setup: context =>
        Behaviors.withTimers: timers =>
          ConfigurationBehavior(
            discoveryActor,
            timers,
            config
          ).active(cachedConfig = None)