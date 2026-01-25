package domain.network

import domain.data.LinearAlgebra.*
import scala.util.Random

/**
 * Configuration container for a single hidden layer.
 */
case class LayerConf(neurons: Int, activation: Activation)


/**
 * Factory object used to construct neural network [[Model]]s.
 */
object ModelBuilder:

  /**
   * Starts the building process by defining the input feature space.
   *
   * @param features Variable arguments listing the enabled input features.
   * @return An initial [[Builder]] instance.
   */
  def fromInputs(features: Feature*): Builder =
    Builder(features.toList, List.empty)

  /**
   * Builder representing the intermediate state of the model construction process.
   * Accumulates configuration before instantiating the final immutable [[Model]].
   */
  private[ModelBuilder] case class Builder(
    features: List[Feature],
    hiddenLayers: List[LayerConf],
    seed: Option[Long] = None,
    outputNeurons: Int = 1,
    outputActivation: Activation = Activations.Sigmoid
  ):

    /**
     * Sets a fixed random seed for reproducible weight initialization.
     */
    def withSeed(s: Long): Builder = this.copy(seed = Some(s))

    /**
     * Appends a new hidden layer to the network topology.
     *
     * @param neurons    The number of neurons in this layer.
     * @param activation The activation function for this layer.
     */
    def addLayer(neurons: Int, activation: Activation): Builder =
      val newConf = LayerConf(neurons, activation)
      this.copy(hiddenLayers = this.hiddenLayers :+ newConf)

    /**
     * Finalizes the configuration and constructs the [[Model]].
     *
     * This method handles the logic for Weight Initialization:
     * It automatically applies the appropriate heuristic (e.g., Xavier/He)
     * based on the specific activation function of each layer.
     *
     * @return A fully initialized [[Model]] ready for inference or training.
     * @throws IllegalArgumentException if no input features are defined.
     */
    def build(): Model =
      require(features.nonEmpty, "No input features defined")

      val rand = seed.map(s => new Random(s)).getOrElse(new Random())
      val inputSize = features.length

      def initWeights(rows: Int, cols: Int, standardDeviation: Double): Matrix =
        Matrix.fill(rows, cols) {
          rand.nextGaussian() * standardDeviation
        }

      val (layers, lastSize) = hiddenLayers.foldLeft((List.empty[Layer], inputSize)) {
        case ((accLayers, inSize), conf) =>
          val stdDev = conf.activation.standardDeviation(inSize, conf.neurons)
          val w = initWeights(conf.neurons, inSize, stdDev)
          val b = Vector.zeros(conf.neurons)
          (accLayers :+ Layer(w, b, conf.activation), conf.neurons)
      }

      val outStdDev = outputActivation.standardDeviation(lastSize, outputNeurons)
      val outW = initWeights(outputNeurons, lastSize, outStdDev)
      val outB = Vector.zeros(outputNeurons)

      val finalNetwork = Network(layers :+ Layer(outW, outB, outputActivation))

      Model(finalNetwork, features)
