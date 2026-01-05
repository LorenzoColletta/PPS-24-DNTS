package domain.network

import domain.data.LinearAlgebra.*
import scala.util.Random

case class LayerConf(neurons: Int, activation: Activation)

object NetworkBuilder:

  def fromInputs(features: Feature*): Builder =
    Builder(features.toList, List.empty)

  case class Builder(
    features: List[Feature],
    hiddenLayers: List[LayerConf],
    seed: Option[Long] = None
  ):
    def withSeed(s: Long): Builder = this.copy(seed = Some(s))

    def addLayer(neurons: Int, activation: Activation): Builder =
      val newConf = LayerConf(neurons, activation)
      this.copy(hiddenLayers = this.hiddenLayers :+ newConf)

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

      val outAct = summon[Activation](using Activations.sigmoid)
      val outW = initWeights(1, lastSize, outAct.standardDeviation(lastSize, 1))
      val outB = Vector.zeros(1)

      val finalNetwork = Network(layers :+ Layer(outW, outB, outAct))

      Model(finalNetwork, features)
