package domain.training.consensus

import domain.network.Network
import domain.data.LinearAlgebra.{Matrix, Vector}

trait ConsensusMetric:
  def divergence(n1: Network, n2: Network): Double

object ConsensusMetric:

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
