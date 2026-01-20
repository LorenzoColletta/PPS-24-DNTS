package domain.training

import domain.data.LinearAlgebra.Vector
import domain.network.Model
import domain.data.{LabeledPoint2D, toVector}
import domain.training.{LossFunction, NetworkGradient}
import domain.training.Consensus.averageGradients

object TrainingCore:

  def computeBatchGradients(
    model: Model,
    batch: List[LabeledPoint2D],
  )(using lossFn: LossFunction): (NetworkGradient, Double) =

    val results = batch.map { ex =>
      val grads = Backpropagation.computeGradients(model.network, ex, model.features)

      val out = Vector(model.predict(ex.point))
      val loss = lossFn.compute(out, ex.label.toVector)

      (NetworkGradient(grads), loss)
    }
    val (gradsList, lossList) = results.unzip

    val avgGrad = if (batch.isEmpty) NetworkGradient(Nil) else averageGradients(gradsList)
    val avgLoss = if (batch.isEmpty) 0.0 else lossList.sum / batch.size.toDouble

    (avgGrad, avgLoss)
