package domain.training

import domain.network.Network
import domain.training.consensus.{ConsensusOps, ConsensusMetric}

object Consensus:

  extension (n1: Network)

    infix def averageWith(n2: Network): Network =
      ConsensusOps.averageModels(n1, n2)

    infix def divergenceFrom(n2: Network)(using metric: ConsensusMetric): Double =
      metric.divergence(n1, n2)

  export ConsensusOps.averageGradients
