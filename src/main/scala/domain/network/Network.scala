package domain.network

import domain.data.Point2D
import domain.data.LinearAlgebra.*
import domain.data.util.Space

/**
 * Configuration container for the hyperparams of the training process.
 *
 * @param learningRate   The step size used by the optimizer.
 * @param regularization The regularization strategy to prevent overfitting.
 */
case class HyperParams(
  learningRate: Double,
  regularization: Regularization = Regularization.None
)

/**
 * Represents a single fully connected layer in a neural network.
 *
 * @param weights    The weight matrix linking inputs to this layer's neurons.
 * @param biases     The bias vector for this layer's neurons.
 * @param activation The non-linear activation function applied to the weighted sum.
 */
case class Layer(
  weights: Matrix,
  biases: Vector,
  activation: Activation
)

/**
 * Represents the topological structure of a neural network.
 * It is a pure sequence of layers without knowledge of the original feature domain.
 */
case class Network(layers: List[Layer]):
  /**
   * Performs the forward pass (inference) through all layers.
   *
   * @param input The input [[Vector]].
   * @return The final output [[Vector]].
   */
  def forward(input: Vector): Vector =
    layers.foldLeft(input) { (x, layer) =>
      val z = (layer.weights * x) + layer.biases
      z.map(layer.activation.apply)
    }

/**
 * Container representing a complete predictive model.
 *
 * @param network  The underlying neural network structure.
 * @param features The feature engineering pipeline configuration.
 */
case class Model(network: Network, features: List[Feature]):

  /**
   * Performs a prediction for a given 2D point.
   *
   * This method first transforms the raw input point into a feature vector
   * according to the model's feature configuration, and then executes a
   * forward pass through the neural network.
   *
   * @param input The raw 2D point to be evaluated.
   * @return The scalar output of the network.
   */
  def predict(input: Point2D)(using Space): Double =
    val vec = FeatureTransformer.transform(input, features)
    network.forward(vec).headOption.getOrElse(0.0)
