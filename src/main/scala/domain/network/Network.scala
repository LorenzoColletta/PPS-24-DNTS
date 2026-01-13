package domain.network

import domain.data.Point2D
import domain.data.LinearAlgebra.*

enum Regularization:
  case None
  case L1(rate: Double)
  case L2(rate: Double)
  case ElasticNet(l1Rate: Double, l2Rate: Double)

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

  def averageWith(remote: Network): Network =
    val averagedLayers = this.layers.zip(remote.layers).map { case (localLayer, remoteLayer) =>
      Layer(
        weights = (localLayer.weights + remoteLayer.weights) / 2.0,
        biases = (localLayer.biases + remoteLayer.biases) / 2.0,
        activation = localLayer.activation // Si assume che l'architettura sia identica
      )
    }
    Network(averagedLayers)
    
object Network:
    def empty: Network = Network(Nil)

case class Model(network: Network, features: List[Feature]):
  def predict(input: Point2D): Double =
    val vec = FeatureTransformer.transform(input, features)
    network.forward(vec).headOption.getOrElse(0.0)
