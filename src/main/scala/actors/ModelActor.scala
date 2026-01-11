package actors

import domain.network.Model

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

enum ModelCommand:
  case TrainingCompleted(newModel: Model)
  case SyncGossipProtocol(remoteModel: Model)

object ModelActor:
  def apply(currentModel: Model): Behavior[ModelCommand] =
    Behaviors.receive: (context, message) =>
      message match
        case ModelCommand.TrainingCompleted(newModel) =>
          context.log.info("ModelActor: Completed Training")
          apply(newModel)
        case ModelCommand.SyncGossipProtocol(remoteModel: Model) =>
          context.log.info("ModelActor: Remote Model")
          apply(remoteModel)