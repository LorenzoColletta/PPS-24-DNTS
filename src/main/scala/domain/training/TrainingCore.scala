package domain.training

import domain.network.{Network, Feature, FeatureTransformer}
import domain.data.{LabeledPoint2D, toVector}
import domain.training.{LossFunction, NetworkGradient}
import domain.training.Consensus.averageGradients

object TrainingCore:

  def computeBatchGradients(
    net: Network,
    batch: List[LabeledPoint2D],
    features: List[Feature]
  )(using lossFn: LossFunction): (NetworkGradient, Double) =

    val results = batch.map { ex =>
      val grads = Backpropagation.computeGradients(net, ex, features)

      val out = net.forward(FeatureTransformer.transform(ex.point, features))
      val loss = lossFn.compute(out, ex.label.toVector)

      (NetworkGradient(grads), loss)
    }
    val (gradsList, lossList) = results.unzip

    val avgGrad = averageGradients(gradsList)
    val avgLoss = lossList.sum / batch.size.toDouble

    (avgGrad, avgLoss)
