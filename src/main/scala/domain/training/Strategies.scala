package domain.training

import scala.math.{signum, max, abs}

import domain.data.LinearAlgebra.*
import domain.network.{Regularization, Network}

object Strategies:

  object Losses:
    given mse: LossFunction with
      def compute(predicted: Vector, target: Vector): Double =
        val diff = predicted - target
        (diff dot diff) / (2.0 * predicted.length)

      def derivative(predicted: Vector, target: Vector): Vector =
        (predicted - target) / predicted.length.toDouble

  object Regularizers:
    private final val none: RegularizationStrategy = (w, _) => w

    private class L2(lambda: Double) extends RegularizationStrategy:
      def apply(w: Matrix, lr: Double): Matrix =
        w * (1.0 - (lr * lambda))

    private class L1(lambda: Double) extends RegularizationStrategy:
      def apply(w: Matrix, lr: Double): Matrix =
        val threshold = lr * lambda
        w.map { x =>
          if (abs(x) <= threshold) 0.0
          else x - (signum(x) * threshold)
        }

    def fromConfig(conf: Regularization): RegularizationStrategy = conf match
      case Regularization.None => none
      case Regularization.L2(l) => L2(l)
      case Regularization.L1(l) => L1(l)
      case Regularization.ElasticNet(l1, l2) =>
        (w, lr) => L1(l1)(L2(l2)(w, lr), lr)

  object Optimizers:
    class SGD(learningRate: Double, reg: RegularizationStrategy) extends Optimizer:
      def updateWeights(net: Network, grads: NetworkGradient): Network =
        require(net.layers.length == grads.layers.length, "Gradient mismatch")

        val newLayers = net.layers.zip(grads.layers).map {
          case (layer, grad) => {
            val regWeights = reg(layer.weights, learningRate)

            val wNew = regWeights + (grad.wGrad * -learningRate)
            val bNew = layer.biases - (grad.bGrad * learningRate)

            layer.copy(weights = wNew, biases = bNew)
          }
        }
        net.copy(layers = newLayers)
