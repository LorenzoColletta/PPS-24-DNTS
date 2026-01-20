package domain.network

import domain.data.Point2D
import domain.data.LinearAlgebra.*


case class HyperParams(
  learningRate: Double,
  regularization: Regularization = Regularization.None
)

case class Layer(
  weights: Matrix,
  biases: Vector,
  activation: Activation
)

case class Network(layers: List[Layer]):
  def forward(input: Vector): Vector =
    layers.foldLeft(input) { (x, layer) =>
      val z = (layer.weights * x) + layer.biases
      z.map(layer.activation.apply)
    }

case class Model(network: Network, features: List[Feature]):
  def predict(input: Point2D): Double =
    val vec = FeatureTransformer.transform(input, features)
    network.forward(vec).headOption.getOrElse(0.0)
