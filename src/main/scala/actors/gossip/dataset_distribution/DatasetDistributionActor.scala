package actors.gossip.dataset_distribution

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import config.{AppConfig, FileConfig}
import actors.discovery.DiscoveryProtocol.{DiscoveryCommand, RegisterGossip}
import actors.root.RootActor.RootCommand


object DatasetDistributionActor:

  export DatasetDistributionProtocol.*

  def apply(
             rootCommand: ActorRef[RootCommand],
             discoveryActor: ActorRef[DiscoveryCommand],
           ): Behavior[DatasetDistributionCommand] =
    Behaviors.setup: context =>
      Behaviors.withTimers: timers =>
        DatasetDistributionBehavior(
          rootCommand,
          discoveryActor
        ).active(seed = None)
