package domain.model


import domain.common.States.State
import domain.network.Model
import domain.training.{NetworkGradient, Optimizer}
import domain.training.Consensus.averageWith

/**
 * Functional tasks for model manipulation.
 * This object contains pure state transitions (using the State monad)
 * for updating and merging model parameters.
 */
object ModelTasks:

  /**
   * Creates a state transition that applies computed gradients to the model.
   *
   * @param grads The gradients to be applied to the current weights.
   * @param opt   The optimizer strategy used to calculate the weight updates.
   * @return A State transition that yields an updated Model.
   */
  def applyGradients(grads: NetworkGradient)(using opt: Optimizer): State[Model, Unit] =
    State: model =>
      val updatedNet = opt.updateWeights(model.network, grads)
      (model.copy(network = updatedNet), ())

  /**
   * Creates a state transition that merges the local model with a remote one.
   *
   * @param remoteModel The model received from a remote peer.
   * @return A State transition that yields a merged Model.
   */
  def mergeWith(remoteModel: Model): State[Model, Unit] =
    State: localModel =>
      val newNet = localModel.network averageWith remoteModel.network
      (localModel.copy(network = newNet), ())