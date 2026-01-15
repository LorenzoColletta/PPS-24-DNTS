package actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors

import domain.network.{Model, Network}
import domain.training.{NetworkGradient, Optimizer}
import domain.training.ModelTasks
import actors.MonitorActor.MonitorCommand

enum ModelCommand:
  case ApplyGradients(grads: NetworkGradient)
  case GetModel(replyTo: ActorRef[Model])
  case SyncModel(remoteModel: Model)
  case TrainingCompleted(updatedModel: Model)
  case GetMetrics(replyTo: ActorRef[MonitorCommand.MetricsResponse])

object ModelActor:

  def apply(initialModel: Model, optimizer: Optimizer): Behavior[ModelCommand] =
    Behaviors.setup: ctx =>
      given Optimizer = optimizer
      active(initialModel)
  private def active(currentModel:
 Model)(using Optimizer): Behavior[ModelCommand] =
    Behaviors.receive: (context, message) =>
      message match
        case ModelCommand.ApplyGradients(grads) =>
          val (newModel, _) = ModelTasks.applyGradients(grads).run(currentModel)
          active(newModel)
        case ModelCommand.SyncModel(remoteModel) =>
          val (newModel, _) = ModelTasks.mergeWith(remoteModel).run(currentModel)
          active(newModel)
        case ModelCommand.GetModel(replyTo) =>
          replyTo ! currentModel
          Behaviors.same
        case ModelCommand.TrainingCompleted(model) =>
          Behaviors.same