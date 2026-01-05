package domain.training

import domain.data.LinearAlgebra.{Matrix, Vector}
import domain.network.Network

case class LayerGradient(wGrad: Matrix, bGrad: Vector)
case class NetworkGradient(layers: List[LayerGradient])

trait LossFunction:
  def compute(predicted: Vector, target: Vector): Double
  def derivative(predicted: Vector, target: Vector): Vector

trait RegularizationStrategy:
  def apply(weights: Matrix, learningRate: Double): Matrix

trait Optimizer:
  def updateWeights(network: Network, gradients: NetworkGradient): Network
