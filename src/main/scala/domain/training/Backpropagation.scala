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

    val (finalOutput, reversedHistory) = network.layers.foldLeft((inputVector, List.empty[LayerCache])) {
      case ((currentInput, historyAcc), layer) =>
        val weightedSum = (layer.weights * currentInput) + layer.biases
        val activationOutput = weightedSum.map(layer.activation.apply)
        val newCache = LayerCache(currentInput, weightedSum, activationOutput)

        (activationOutput, newCache :: historyAcc)
    }

    val lastLayerCache = reversedHistory.head
    val initialErrorSignal = loss.derivative(lastLayerCache.activationOutput, targetVector)

    val backwardSteps = network.layers.reverse.zip(reversedHistory)
    val (gradients, _) = backwardSteps.foldLeft((List.empty[LayerGradient], initialErrorSignal)) {
      case ((gradsAcc, currentErrorSignal), (layer, cache)) =>

        val activationDerivative = cache.weightedSum.map(layer.activation.derivative)
        val localGradient = currentErrorSignal |*| activationDerivative

        val weightGradient = localGradient outer cache.input
        val biasGradient = localGradient

        val errorSignalForPreviousLayer = layer.weights.T * localGradient

        (LayerGradient(weightGradient, biasGradient) :: gradsAcc, errorSignalForPreviousLayer)
    }
    gradients
