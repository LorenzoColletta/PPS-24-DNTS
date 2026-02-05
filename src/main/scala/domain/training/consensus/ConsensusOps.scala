package domain.training.consensus

import domain.network.Network
import domain.training.{NetworkGradient, LayerGradient}

/**
 * Low-level operations for aggregating network states and gradients.
 */
object ConsensusOps:

  /**
   * Computes the parameter-wise arithmetic mean of two neural networks.
   * Both networks must share the exact same topology.
   *
   * @param n1 The first [[Network]].
   * @param n2 The second [[Network]].
   * @return A new [[Network]] instance containing the averaged weights and biases.
   * @throws IllegalArgumentException if the network topologies differ.
   */
  def averageModels(n1: Network, n2: Network): Network =
    require(n1.layers.length == n2.layers.length, "Topology mismatch")

    val newLayers = n1.layers.zip(n2.layers).map { (l1, l2) =>
      require(l1.weights.rows == l2.weights.rows && l1.weights.cols == l2.weights.cols)

      val avgWeights = (l1.weights + l2.weights) * 0.5
      val avgBiases = (l1.biases + l2.biases) * 0.5

      l1.copy(weights = avgWeights, biases = avgBiases)
    }
    Network(newLayers)

  /**
   * Aggregates a list of [[NetworkGradient]]s into a single average [[NetworkGradient]].
   *
   * @param grads The list of gradients to combine. Must not be empty.
   * @return A single [[NetworkGradient]] representing the average direction and magnitude.
   * @throws IllegalArgumentException if the list of gradients is empty.
   */
  def averageGradients(grads: List[NetworkGradient]): NetworkGradient =
    require(grads.nonEmpty, "Cannot average empty gradients")
    if grads.length == 1 then return grads.head

    val scale = 1.0 / grads.length.toDouble

    val sumGrads = grads.reduce { (g1, g2) =>
      val summedLayers = g1.layers.zip(g2.layers).map {
        case (l1, l2) =>
          LayerGradient(wGrad = l1.wGrad + l2.wGrad, bGrad = l1.bGrad + l2.bGrad)
      }
      NetworkGradient(summedLayers)
    }

    val avgLayers = sumGrads.layers.map {
      l =>
        LayerGradient(wGrad = l.wGrad * scale, bGrad = l.bGrad * scale)
    }
    NetworkGradient(avgLayers)
