package actors.gossip.dataset_distribution

import actors.discovery.DiscoveryProtocol.{DiscoveryCommand, NodesRefRequest}
import actors.gossip.GossipProtocol.GossipCommand
import actors.gossip.dataset_distribution.DatasetDistributionProtocol.DatasetDistributionCommand
import actors.root.RootProtocol.RootCommand
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

/**
 * Encapsulates the behavior logic for the DatasetDistributionActor.
 *
 * @param rootActor       Reference to the local [[RootActor]].
 * @param discoveryActor  Reference to the [[DiscoveryActor]] for peer discovery.
 */
private[dataset_distribution] class DatasetDistributionBehavior(
  rootActor: ActorRef[RootCommand],
  discoveryActor: ActorRef[DiscoveryCommand]
):

  /**
   * Main behavior for managing the partitioning and distribution of datasets across the cluster.
   *
   * @param seed An optional randomization seed used for shuffling the dataset before distribution.
   *
   * @return A behavior that handles the partitioning and dispatching of training shards to discovered nodes.
   */
  private[dataset_distribution] def active(seed: Option[Long] = None): Behavior[DatasetDistributionCommand] =
    Behaviors.receive: (context, message) =>
      message match
        case DatasetDistributionProtocol.RegisterSeed(seed) =>
          active(Some(seed))

        case DatasetDistributionProtocol.DistributeDataset(trainSet, testSet) =>
          discoveryActor ! NodesRefRequest(
            replyTo = context.messageAdapter(peers =>
              DatasetDistributionProtocol.WrappedDistributeDataset(peers, trainSet, testSet)
            )
          )
          Behaviors.same

        case DatasetDistributionProtocol.WrappedDistributeDataset(peers, trainSet, testSet) =>
          val totalNodes = peers.size
          if totalNodes > 0 then

            val randomizer = new scala.util.Random(seed.getOrElse(0L))

            val shuffledTrainSet = randomizer.shuffle(trainSet)

            val chunkSize = shuffledTrainSet.size / totalNodes

            peers.zipWithIndex.foreach: (peer, index) =>
              val from = index * chunkSize
              val until = if (index == totalNodes - 1) shuffledTrainSet.size else (index + 1) * chunkSize
              val trainShard = shuffledTrainSet.slice(from, until)

              peer ! DatasetDistributionProtocol.HandleDistributeDataset(trainShard, testSet)
          Behaviors.same

        case DatasetDistributionProtocol.HandleDistributeDataset(trainShard, testSet) =>
          rootActor ! RootCommand.DistributedDataset(trainShard, testSet)
          Behaviors.same

        case DatasetDistributionProtocol.Stop =>
          context.log.info("DatasetDistribution: Stopping actor.")
          Behaviors.stopped
