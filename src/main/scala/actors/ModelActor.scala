package actors

import domain.network.Model
import domain.model.ModelTasks
import domain.training.{NetworkGradient, Optimizer}
import actors.monitor.MonitorActor.MonitorCommand
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

object ModelActor:
  
  enum ModelCommand:
    case ApplyGradients(grads: NetworkGradient)
    case GetModel(replyTo: ActorRef[Model])
    case SyncModel(remoteModel: Model)
    case TrainingCompleted(updatedModel: Model)
    case GetMetrics(replyTo: ActorRef[MonitorCommand.ViewUpdateResponse])
    case ExportToFile()
    case Initialize(model: Model, optimizer: Optimizer)
    
  def apply(): Behavior[ModelCommand] =
    Behaviors.setup: ctx =>
      idle()

  private def idle(): Behavior[ModelCommand] =
    Behaviors.receive: (ctx, msg) =>
      msg match
        case ModelCommand.Initialize(model, optimizer) =>
          ctx.log.info("Model: Model initialized. Switching to active state.")
          given Optimizer = optimizer
          active(model)

        case _ => Behaviors.unhandled
      
  private def active(currentModel: Model)(using Optimizer): Behavior[ModelCommand] =
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