package actors.model

import actors.model.ModelActor.ModelCommand
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.Behavior
import config.{AppConfig, ProductionConfig}

/**
 * This actor is responsible for maintaining the model's state, applying
 * gradients to update the weights, and managing the synchronization of
 * requests from the gossip cluster.
 */
object ModelActor:

  export ModelProtocol.*

  /**
   * Creates the initial behavior for the ModelActor.
   *
   * It initializes the actor in an 'idle' state, waiting for the mandatory
   * configuration and initial model structure to be provided via the Initialize command.
   *
   * @return A Behavior handling ModelCommand messages.
   */
  def apply(): Behavior[ModelCommand] =
    val config : AppConfig = ProductionConfig
    Behaviors.setup: ctx =>
      ModelBehavior(ctx, config).idle()