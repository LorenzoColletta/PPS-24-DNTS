package actors.gossip.dataset_distribution

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import config.{AppConfig, FileConfig}
import actors.discovery.DiscoveryProtocol.{DiscoveryCommand, RegisterGossip}
import actors.root.RootActor.RootCommand

/** Actor responsible for distributing the dataset among peers */
object DatasetDistributionActor:

  export DatasetDistributionProtocol.*

  /**
   * Creates the behavior for the DatasetDistributionActor.
   *
   * @param rootCommand    Reference to the local [[RootActor]].
   * @param discoveryActor Reference to the [[DiscoveryActor]] for peer discovery.
   *
   * @return A Behavior handling DatasetDistributionCommand messages.
   */
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
