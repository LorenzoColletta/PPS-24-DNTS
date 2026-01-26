package domain.training

import domain.network.Network
import domain.training.consensus.{ConsensusOps, ConsensusMetric}

/**
 * Facade object providing high-level consensus and synchronization primitives for [[Network]]s.
 */
object Consensus:

  extension (n1: Network)

    /**
     * Computes the arithmetic mean of the parameters (weights and biases) of this network and another.
     *
     * @param n2 The other network to average with. Must have the exact same topology.
     * @return A new [[Network]] updated instance.
     */
    infix def averageWith(n2: Network): Network =
      ConsensusOps.averageModels(n1, n2)

    /**
     * Calculates the quantitative difference between this network and another.
     *
     * @param n2     The network to compare against.
     * @param metric The implicit strategy used to calculate the distance.
     * @return A scalar double representing the magnitude of the difference.
     */
    infix def divergenceFrom(n2: Network)(using metric: ConsensusMetric): Double =
      metric.divergence(n1, n2)

  /**
   * Exports the gradient aggregation utility for direct access.
   * Allows averaging a list of [[NetworkGradient]]s.
   */
  export ConsensusOps.averageGradients
