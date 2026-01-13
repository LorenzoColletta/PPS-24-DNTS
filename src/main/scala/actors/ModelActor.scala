package actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors

import domain.network.{Model, Network}
import domain.training.{NetworkGradient, Optimizer}
import domain.training.ModelTasks
import actors.MonitorActor.MonitorCommand

object ModelActor:

  enum ModelCommand:
    case ApplyGradients(grads: NetworkGradient)
    case GetModel(replyTo: ActorRef[Network])
    case SyncModel(remoteModel: Network)
    case TrainingCompleted(updatedModel: Model)
    case GetMetrics(replyTo: ActorRef[MonitorCommand.MetricsResponse])


  def apply(initialNetwork: Network, optimizer: Optimizer): Behavior[ModelCommand] =
    Behaviors.setup: ctx =>
      given Optimizer = optimizer
      active(initialNetwork)

  private def active(currentNetwork: Network)(using Optimizer): Behavior[ModelCommand] =
    Behaviors.receive: (context, message) =>
      message match
        case ModelCommand.ApplyGradients(grads) =>
          val (newNetwork, _) = ModelTasks.applyGradients(grads).run(currentNetwork)
          active(newNetwork)
        case ModelCommand.SyncModel(remoteModel) =>
          val (newNetwork, _) = ModelTasks.mergeWith(remoteModel).run(currentNetwork)
          active(newNetwork)
        case ModelCommand.GetModel(replyTo) =>
          replyTo ! currentNetwork
          Behaviors.same
        case ModelCommand.TrainingCompleted(model) =>
          Behaviors.same
