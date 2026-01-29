package domain.training.consensus

import domain.network.Network

/**
 * Strategy interface for calculating the quantitative difference between two neural networks.
 */
trait ConsensusMetric:
  /**
   * Computes a scalar value representing the dissimilarity between two network states.
   *
   * @param n1 The first network.
   * @param n2 The second network.
   * @return A [[Double]] where 0.0 implies identical networks, and higher values imply greater difference.
   */
  def divergence(n1: Network, n2: Network): Double

object ConsensusMetric:

  /**
   * Default implementation using Mean Absolute Error (MAE) on all parameters.
   * It calculates the average absolute difference across all weights and biases.
   */
  given default: ConsensusMetric with
    def divergence(n1: Network, n2: Network): Double =
      require(n1.layers.length == n2.layers.length, "Topology mismatch")

      val (diffSum, paramCount) = n1.layers.zip(n2.layers).foldLeft((0.0, 0)) {
        case ((accDiff, accCount), (l1, l2)) =>
          val wDiff = (l1.weights - l2.weights).map(math.abs).toFlatList.sum
          val bDiff = (l1.biases - l2.biases).map(math.abs).toList.sum

          val params = (l1.weights.rows * l1.weights.cols) + l1.biases.length
          (accDiff + wDiff + bDiff, accCount + params)
      }

      if paramCount > 0 then diffSum / paramCount else 0.0
