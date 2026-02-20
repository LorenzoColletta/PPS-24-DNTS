package actors.gossip.configuration

import actors.gossip.GossipProtocol.GossipCommand
import actors.gossip.configuration.ConfigurationProtocol.ConfigurationCommand
import domain.network.Model
import actors.trainer.TrainerActor.TrainingConfig
import akka.actor.typed.ActorRef

object ConfigurationProtocol:

  sealed trait ConfigurationCommand extends GossipCommand

  final case class RegisterGossip(gossipActor: ActorRef[GossipCommand]) extends ConfigurationCommand

  case object StartTickRequest extends ConfigurationCommand

  case object StopTickRequest extends ConfigurationCommand

  final case class ShareConfig(
                                seedID: String,
                                model: Model,
                                config: TrainingConfig
                              ) extends ConfigurationCommand

  final case class RequestInitialConfig(replyTo: ActorRef[ConfigurationCommand]) extends ConfigurationCommand

  final case class WrappedRequestConfig(peers: Set[ActorRef[ConfigurationCommand]]) extends ConfigurationCommand

  case object TickRequest extends ConfigurationCommand

  /**
   * Stop the actor.
   */
  case object Stop extends ConfigurationCommand

