package actors.trainer

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

import config.AppConfig
import domain.training.LossFunction
import actors.trainer.TrainerBehavior
import actors.ModelActor.ModelCommand


/**
 * Actor responsible for the actual training loop logic.
 * It manages the dataset iteration (epochs/batches), computes gradients using the
 * TrainingCore, and sends updates back to the ModelActor.
 */
object TrainerActor:

  export TrainerProtocol.*
  
  /**
   * Creates the TrainerActor behavior.
   *
   * @param modelActor    Reference to the ModelActor (holds the shared state).
   * @param lossFunction  Implicit loss function used for gradient calculation.
   * @param config        Implicit application configuration.
   */
  def apply(
    modelActor: ActorRef[ModelCommand],
  )(using lossFunction: LossFunction, config: AppConfig): Behavior[TrainerMessage] =

    Behaviors.setup { context =>
      Behaviors.withTimers { timers =>
        new TrainerBehavior(context, timers, modelActor).idle()
      }
    }
