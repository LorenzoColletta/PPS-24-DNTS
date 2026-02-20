package actors.gossip.dataset_distribution

import actors.discovery.DiscoveryProtocol.{DiscoveryCommand, NodesRefRequest}
import actors.gossip.GossipProtocol.GossipCommand
import actors.gossip.consensus.ConsensusProtocol.ConsensusCommand
import actors.gossip.dataset_distribution.DatasetDistributionProtocol.DatasetDistributionCommand
import actors.root.RootProtocol.RootCommand
import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import config.AppConfig


private[dataset_distribution] class DatasetDistributionBehavior(
                                                                 rootActor: ActorRef[RootCommand],
                                                                 discoveryActor: ActorRef[DiscoveryCommand]
                                                               ):
  private[dataset_distribution] def active(): Behavior[DatasetDistributionCommand] =
    Behaviors.receive: (context, message) =>
      message match
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
            val chunkSize = trainSet.size / totalNodes

            peers.zipWithIndex.foreach: (peer, index) =>
              val from = index * chunkSize
              val until = if (index == totalNodes - 1) trainSet.size else (index + 1) * chunkSize
              val trainShard = trainSet.slice(from, until)

              peer ! DatasetDistributionProtocol.HandleDistributeDataset(trainShard, testSet)
          Behaviors.same

        case DatasetDistributionProtocol.HandleDistributeDataset(trainShard, testSet) =>
          rootActor ! RootCommand.DistributedDataset(trainShard, testSet)
          Behaviors.same