package domain.model


import domain.training.Consensus.averageWith
import domain.common.States.State
import domain.network.{Model, Network}
import domain.training.{NetworkGradient, Optimizer}

object ModelTasks:

  def applyGradients(grads: NetworkGradient)(using opt: Optimizer): State[Model, Unit] =
    State: model =>
      val updatedNet = opt.updateWeights(model.network, grads)
      (model.copy(network = updatedNet), ())

  def mergeWith(remoteModel: Model): State[Model, Unit] =
    State: localModel =>
      val newNet = localModel.network averageWith remoteModel.network
      (localModel.copy(network = newNet), ())