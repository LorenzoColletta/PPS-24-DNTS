package actors.gossip.dataset_distribution

import actors.gossip.GossipProtocol.GossipCommand
import akka.actor.typed.ActorRef
import domain.data.LabeledPoint2D

object DatasetDistributionProtocol:

  sealed trait DatasetDistributionCommand extends GossipCommand

  final case class RegisterSeed(seed: Long) extends DatasetDistributionCommand

  /**
   *
   * Command for the Master node to distribute the dataset to the cluster peers.
   *
   * @param trainSet The complete training dataset.
   * @param testSet  The complete test dataset.
   */
  final case class DistributeDataset(
                                      trainSet: List[LabeledPoint2D],
                                      testSet: List[LabeledPoint2D]
                                    ) extends DatasetDistributionCommand

  /**
   *
   * Internal wrapper for dataset distribution after peer discovery.
   *
   * @param peers    Peer to send the portion of the dataset.
   * @param trainSet The complete training dataset.
   * @param testSet  The complete test dataset.
   * */
  final case class WrappedDistributeDataset(
                                             peers: List[ActorRef[DatasetDistributionCommand]],
                                             trainSet: List[LabeledPoint2D],
                                             testSet: List[LabeledPoint2D]
                                           ) extends DatasetDistributionCommand

  /**
   * Message received by a Client node containing its portion of the data.
   *
   * @param trainShard The slice of training data for the local node.
   * @param testSet    The full test set for local evaluation.
   */
  final case class HandleDistributeDataset(
                                            trainShard: List[LabeledPoint2D],
                                            testSet: List[LabeledPoint2D]
                                          ) extends DatasetDistributionCommand

  /**
   * Stop the actor.
   */
  case object Stop extends DatasetDistributionCommand