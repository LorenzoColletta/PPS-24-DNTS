package domain.training

import domain.data.LinearAlgebra.*
import domain.data.{Label, LabeledPoint2D}
import domain.data.toVector
import domain.network.*

object Backpropagation:

  private case class LayerCache(input: Vector, weightedSum: Vector, activationOutput: Vector)

  def computeGradients(
    network: Network,
    example: LabeledPoint2D,
    features: List[Feature]
  )(using loss: LossFunction): List[LayerGradient] =

    val targetVector = example.label.toVector
    val inputVector = FeatureTransformer.transform(example.point, features)

    val history = runForwardPassWithCache(network, inputVector)

    runBackwardPass(network, history, targetVector, loss)


  private def runForwardPassWithCache(network: Network, input: Vector): List[LayerCache] =
    val (_, reversedHistory) = network.layers.foldLeft((input, List.empty[LayerCache])) {
      case ((currentInput, historyAcc), layer) =>
        val weightedSum = (layer.weights * currentInput) + layer.biases
        val activationOutput = weightedSum.map(layer.activation.apply)

        val newCache = LayerCache(currentInput, weightedSum, activationOutput)
        (activationOutput, newCache :: historyAcc)
    }
    reversedHistory

  private def runBackwardPass(
     network: Network,
     reversedHistory: List[LayerCache],
     target: Vector,
     loss: LossFunction
  ): List[LayerGradient] =

    val lastLayerCache = reversedHistory.head
    val initialErrorSignal = loss.derivative(lastLayerCache.activationOutput, target)

    val backwardSteps = network.layers.reverse.zip(reversedHistory)

    val (gradients, _) = backwardSteps.foldLeft((List.empty[LayerGradient], initialErrorSignal)) {
      case ((gradsAcc, currentErrorSignal), (layer, cache)) =>
        val (layerGrad, prevErrorSignal) = computeSingleLayerGradient(layer, cache, currentErrorSignal)

        (layerGrad :: gradsAcc, prevErrorSignal)
    }
    gradients

  private def computeSingleLayerGradient(
    layer: Layer,
    cache: LayerCache,
    errorSignal: Vector
  ): (LayerGradient, Vector) =

    val activationDerivative = cache.weightedSum.map(layer.activation.derivative)
    val localDelta = errorSignal |*| activationDerivative

    val weightGradient = localDelta outer cache.input
    val biasGradient = localDelta

    val errorForPreviousLayer = layer.weights.T * localDelta

    (LayerGradient(weightGradient, biasGradient), errorForPreviousLayer)
