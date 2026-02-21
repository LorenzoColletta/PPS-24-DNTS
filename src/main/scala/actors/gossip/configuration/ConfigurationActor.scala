package actors.gossip.configuration

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import config.AppConfig
import actors.discovery.DiscoveryProtocol.DiscoveryCommand

/**
 * Actor responsible for distributing the initial configuration from the seed to the client nodes.
 */
object ConfigurationActor:
  export ConfigurationProtocol.*

  /**
   * Creates the initial behavior for the ConfigurationActor.
   *
   * @param discoveryActor Reference to the [[DiscoveryActor]] for peer discovery.
   * @param config         Shared configuration
   *
   * @return A Behavior handling ConfigurationCommand messages.
   */
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