package domain.training

import scala.util.Random
import domain.network.{Feature, FeatureTransformer, HyperParams, Network}
import domain.data.{Label, LabeledPoint2D}
import domain.data.toVector
import domain.data.LinearAlgebra.*
import Strategies.Regularizers
import Strategies.Optimizers
import Strategies.Losses.mse

object TrainingService:

  def train(
    initialNetwork: Network,
    dataset: List[LabeledPoint2D],
    features: List[Feature],
    hp: HyperParams,
    epochs: Int,
    batchSize: Int,
    seed: Option[Long] = None
  ): (Network, List[Double]) =

    require(batchSize > 0, "Batch size must be positive")
    require(epochs > 0, "Epochs must be positive")

    val rand = seed.map(s => new Random(s)).getOrElse(new Random())

    val regStrategy = Regularizers.fromConfig(hp.regularization)
    val optimizer = new Optimizers.SGD(hp.learningRate, regStrategy)
    val lossFn = summon[LossFunction]

    val (trainedNetwork, lossHistory) = (1 to epochs).foldLeft((initialNetwork, List.empty[Double])) {
      case ((currentNet, lossAcc), _) =>
        val (epochNet, epochAvgLoss) =
          runEpoch(
            currentNet,
            dataset,
            features,
            optimizer,
            batchSize,
            lossFn,
            rand
          )
        (epochNet, lossAcc :+ epochAvgLoss)
    }
    (trainedNetwork, lossHistory)


  private def runEpoch(
    network: Network,
    dataset: List[LabeledPoint2D],
    features: List[Feature],
    optimizer: Optimizer,
    batchSize: Int,
    lossFn: LossFunction,
    rand: Random
  ): (Network, Double) =

    val shuffledData = rand.shuffle(dataset)
    val batches = shuffledData.grouped(batchSize).toList

    val (finalNet, totalEpochLoss) = batches.foldLeft((network, 0.0)) {
      case ((netState, currentLossSum), batch) =>
        val (updatedNet, batchLoss) = processBatch(netState, batch, features, optimizer, lossFn)
        (updatedNet, currentLossSum + batchLoss)
    }
    (finalNet, totalEpochLoss / dataset.size.toDouble)

  private def processBatch(
    network: Network,
    batch: List[LabeledPoint2D],
    features: List[Feature],
    optimizer: Optimizer,
    lossFn: LossFunction
  ): (Network, Double) =

    val batchResults = batch.map { example =>
      computeExampleGradientsAndLoss(network, example, features, lossFn)
    }
    val (gradsList, lossesList) = batchResults.unzip

    val avgGrads = averageGradients(gradsList)
    val newNetwork = optimizer.updateWeights(network, avgGrads)

    (newNetwork, lossesList.sum)

  private def computeExampleGradientsAndLoss(
    network: Network,
    example: LabeledPoint2D,
    features: List[Feature],
    lossFn: LossFunction
  ): (NetworkGradient, Double) =

    val grads = Backpropagation.computeGradients(network, example, features)(using lossFn)

    val prediction = network.forward(FeatureTransformer.transform(example.point, features))
    val error = lossFn.compute(prediction, example.label.toVector)

    (NetworkGradient(grads), error)

  private def averageGradients(grads: List[NetworkGradient]): NetworkGradient =
    val batchSize = grads.length

    if batchSize == 1 then grads.head
    else
      val scale = 1.0 / batchSize.toDouble

      val sumGrads = grads.reduce { (g1, g2) =>
        val summedLayers = g1.layers.zip(g2.layers).map { case (l1, l2) =>
          LayerGradient(
            wGrad = l1.wGrad + l2.wGrad,
            bGrad = l1.bGrad + l2.bGrad
          )
        }
        NetworkGradient(summedLayers)
      }

      val avgLayers = sumGrads.layers.map { l =>
        LayerGradient(
          wGrad = l.wGrad * scale,
          bGrad = l.bGrad * scale
        )
      }
      NetworkGradient(avgLayers)
