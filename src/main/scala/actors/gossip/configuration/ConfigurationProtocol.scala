package actors.gossip.configuration

import actors.gossip.GossipProtocol.GossipCommand
import domain.network.Model
import actors.trainer.TrainerActor.TrainingConfig
import akka.actor.typed.ActorRef

/**
 * Defines the public API and data structures for the Configuration component.
 */
object ConfigurationProtocol:

  /** Base trait for all configuration-related messages. */
  sealed trait ConfigurationCommand extends GossipCommand

  /**
   * Registers the local Gossip actor.
   *
   * @param gossipActor Reference to the local [[GossipActor]]
   */
  final case class RegisterGossip(gossipActor: ActorRef[GossipCommand]) extends ConfigurationCommand

  /** Starts the periodic timer for configuration request polling. */
  case object StartTickRequest extends ConfigurationCommand

  /** Stops the periodic timer for configuration request polling. */
  case object StopTickRequest extends ConfigurationCommand

  /**
   * Command used to share the configuration within the node.
   * @param seedID      The unique identifier of the seed node.
   * @param model       The model architecture.
   * @param config      The global training hyperparameters.
   */
  final case class ShareConfig(
                                seedID: String,
                                model: Model,
                                config: TrainingConfig
                              ) extends ConfigurationCommand

  /**
   * Sent to a peer to request its current configuration state.
   *
   * @param replyTo The ActorRef that will receive the ShareConfig response.
   */
  final case class RequestInitialConfig(replyTo: ActorRef[ConfigurationCommand]) extends ConfigurationCommand

  /**
   * Internal wrapper to handle a set of peers discovered through the network.
   *
   * @param peers The set of active configuration actors in the cluster.
   */
  final case class WrappedRequestConfig(peers: Set[ActorRef[ConfigurationCommand]]) extends ConfigurationCommand

  /**
   * Triggered by a timer to share the configuration.
   */
  case object TickRequest extends ConfigurationCommand

  /**
   * Stop the actor.
   */
  case object Stop extends ConfigurationCommand

