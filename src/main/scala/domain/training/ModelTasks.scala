package domain.training

import domain.common.States.State
import domain.network.Network

object ModelTasks:

  def applyGradients(grads: NetworkGradient)(using opt: Optimizer): State[Network, Unit] =
    State { net =>
      val updatedNet = opt.updateWeights(net, grads)
      (updatedNet, ())
    }

  def mergeWith(remoteNet: Network): State[Network, Unit] =
    State { localNet =>
      val averagedNet = localNet.averageWith(remoteNet)
      (averagedNet, ())
    }