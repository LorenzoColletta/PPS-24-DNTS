package actors.gossip

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.ActorRef
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers
import actors.gossip.dataset_distribution.DatasetDistributionActor
import actors.gossip.dataset_distribution.DatasetDistributionProtocol.*
import actors.discovery.DiscoveryProtocol.{DiscoveryCommand, NodesRefRequest}
import actors.gossip.GossipProtocol.GossipCommand
import actors.root.RootActor.RootCommand
import domain.data.{Label, LabeledPoint2D, Point2D}

class DatasetDistributionActorTest extends ScalaTestWithActorTestKit with AnyFunSuiteLike with Matchers {

  private val testPoint = LabeledPoint2D(Point2D(0.5, 0.5), Label.Positive)
  private val trainSet = List.fill(10)(testPoint)
  private val testSet = List(testPoint)

  private def setup() = {
    val rootProbe = createTestProbe[RootCommand]()
    val discoveryProbe = createTestProbe[DiscoveryCommand]()
    val distributionActor = spawn(DatasetDistributionActor(rootProbe.ref, discoveryProbe.ref))
    (distributionActor, rootProbe, discoveryProbe)
  }

  test("DatasetDistributionActor should request node references from DiscoveryActor upon DistributeDataset") {
    val (distActor, _, discoveryProbe) = setup()
    distActor ! DistributeDataset(trainSet, testSet)
    val request = discoveryProbe.expectMessageType[NodesRefRequest]
    request.replyTo shouldBe a[ActorRef[_]]
  }

  test("DatasetDistributionActor should split the dataset equally among received peers") {
    val (distActor, _, discoveryProbe) = setup()

    val peer1 = createTestProbe[DatasetDistributionCommand]()
    val peer2 = createTestProbe[DatasetDistributionCommand]()

    val peersForDiscovery: List[ActorRef[GossipCommand]] = List(
      peer1.ref.unsafeUpcast[GossipCommand],
      peer2.ref.unsafeUpcast[GossipCommand]
    )

    distActor ! DistributeDataset(trainSet, testSet)

    val request = discoveryProbe.expectMessageType[NodesRefRequest]

    request.replyTo ! peersForDiscovery

    val msg1 = peer1.expectMessageType[HandleDistributeDataset]
    val msg2 = peer2.expectMessageType[HandleDistributeDataset]

    msg1.trainShard.size shouldBe 5
    msg2.trainShard.size shouldBe 5
    msg1.testSet shouldBe testSet
  }

  test("DatasetDistributionActor should handle shuffling correctly with a registered seed") {
    val (distActor, _, discoveryProbe) = setup()
    val peerProbe = createTestProbe[DatasetDistributionCommand]()

    val dataset = (1 to 10).map(i => LabeledPoint2D(Point2D(i, i), Label.Positive)).toList
    val seed = 42L

    distActor ! RegisterSeed(seed)
    distActor ! DistributeDataset(dataset, Nil)

    val request = discoveryProbe.expectMessageType[NodesRefRequest]

    val peers = List(peerProbe.ref.unsafeUpcast[GossipCommand])

    request.replyTo ! peers

    val receivedData = peerProbe.expectMessageType[HandleDistributeDataset].trainShard
    receivedData should contain theSameElementsAs dataset
    receivedData should not be dataset
  }

  test("DatasetDistributionActor should forward DistributedDataset to RootActor when handling a shard") {
    val (distActor, rootProbe, _) = setup()
    val shard = List(testPoint)
    distActor ! HandleDistributeDataset(shard, testSet)

    val rootMsg = rootProbe.expectMessageType[RootCommand.DistributedDataset]

    rootMsg.trainShard shouldBe shard
    rootMsg.testSet shouldBe testSet
  }

  test("DatasetDistributionActor should stop when receiving the Stop command") {
    val (distActor, _, _) = setup()
    distActor ! Stop
    createTestProbe().expectTerminated(distActor)
  }
}