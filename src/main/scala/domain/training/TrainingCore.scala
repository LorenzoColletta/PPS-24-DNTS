package domain.training

import domain.data.LinearAlgebra.Vector
import domain.data.util.Space
import domain.network.Model
import domain.data.{LabeledPoint2D, toVector}
import domain.training.Consensus.averageGradients

/**
 * Core object responsible for the numerical computation of training steps.
 * It acts as a bridge between the high-level Actors and the low-level math of Backpropagation and LinearAlgebra.
 */
object TrainingCore:

  /**
   * Computes the gradients and the loss for a specific batch of data.
   * This is used during the training phase to update the model weights.
   *
   * @param model  The current [[Model]] containing the network and feature configuration.
   * @param batch  The subset of training data to process.
   * @param lossFn The implicit [[LossFunction]] to evaluate the prediction error.
   * @return A tuple containing:
   *         - [[NetworkGradient]]: The averaged gradients for this batch.
   *         - [[Double]]: The average loss value for this batch.
   */
  def computeBatchGradients(
    model: Model,
    batch: List[LabeledPoint2D],
  )(using lossFn: LossFunction, space: Space): (NetworkGradient, Double) =

    val results = batch.map { ex =>
      val grads = Backpropagation.computeGradients(model.network, ex, model.features)

      val prediction = model.predict(ex.point)
      val predictionVec = Vector(prediction)
      val loss = lossFn.compute(predictionVec, ex.label.toVector)

      (NetworkGradient(grads), loss)
    }
    val (gradsList, lossList) = results.unzip

    val avgGrad = if (batch.isEmpty) NetworkGradient(Nil) else averageGradients(gradsList)
    val avgLoss = if (batch.isEmpty) 0.0 else lossList.sum / batch.size.toDouble

    (avgGrad, avgLoss)

  /**
   * Computes the average loss over an entire dataset.
   *
   * @param model  The current [[Model]] containing the network and feature configuration.
   * @param data   The list of data points.
   * @param lossFn The implicit [[LossFunction]] to evaluate the prediction error.
   * @param space  The implicit definition of the boundaries of the 2D plane used.
   * @return The average loss value over the provided dataset.
   */
  def computeDatasetLoss(
    model: Model,
    data: List[LabeledPoint2D]
  )(using lossFn: LossFunction, space: Space): Double =

    if data.isEmpty then 0.0
    else
      val totalLoss = data.map { ex =>
        val prediction = model.predict(ex.point)
        lossFn.compute(Vector(prediction), ex.label.toVector)
      }.sum
      totalLoss / data.size.toDouble
