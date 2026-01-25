package actors.model

import actors.model.ModelActor.ModelCommand
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import domain.model.ModelTasks
import domain.training.Optimizer
import domain.network.Model

private[model] class ModelBehavior(context: ActorContext[ModelCommand]):

  def idle(): Behavior[ModelCommand] =
    Behaviors.receive: (ctx, msg) =>
      msg match
        case ModelCommand.Initialize(model, optimizer) =>
          ctx.log.info("Model: Model initialized. Switching to active state.")
          given Optimizer = optimizer
          active(model)

        case _ => Behaviors.unhandled


  private def active(currentModel: Model)(using Optimizer): Behavior[ModelCommand] =
    Behaviors.receive: (cxt, message) =>
      message match
        case ModelCommand.ApplyGradients(grads) =>
          val (newModel, _) = ModelTasks.applyGradients(grads).run(currentModel)
          active(newModel)
        case ModelCommand.SyncModel(remoteModel) =>
          val (newModel, _) = ModelTasks.mergeWith(remoteModel).run(currentModel)
          active(newModel)
        case ModelCommand.GetPrediction(point, replyTo) =>
          val prediction = currentModel.predict(point)
          replyTo ! prediction
          Behaviors.same
        case ModelCommand.GetModel(replyTo) =>
          replyTo ! currentModel
          Behaviors.same
        case ModelCommand.TrainingCompleted(model) =>
          active(model)