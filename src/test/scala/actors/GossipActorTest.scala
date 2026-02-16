package actors

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.ActorRef
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers

import actors.gossip.GossipActor
import actors.gossip.GossipProtocol.*
import actors.gossip.GossipActor.GossipCommand
import actors.model.ModelActor.ModelCommand
import actors.trainer.TrainerActor.{TrainerCommand, TrainingConfig}
import actors.discovery.DiscoveryProtocol.{DiscoveryCommand, RegisterGossip, NodesRefRequest}
import actors.root.RootActor.RootCommand

import domain.network.{Activations, Feature, ModelBuilder, Regularization, HyperParams}
import domain.data.{Label, LabeledPoint2D, Point2D}
import config.{AppConfig, ProductionConfig}

class GossipActorTest extends ScalaTestWithActorTestKit with AnyFunSuiteLike with Matchers {

  given AppConfig = ProductionConfig

  private final val dummyFeatures = Feature.X

  private final val dummyModel = ModelBuilder.fromInputs(dummyFeatures)
    .addLayer(neurons = 1, activation = Activations.Sigmoid)
    .withSeed(1234L)
    .build()

  private final val dummyData = List(
    LabeledPoint2D(Point2D(0.0, 0.0), Label.Negative),
    LabeledPoint2D(Point2D(1.0, 1.0), Label.Positive),
    LabeledPoint2D(Point2D(0.0, 1.0), Label.Negative),
    LabeledPoint2D(Point2D(1.0, 0.0), Label.Positive)
  )

  private final val dummyConfig = TrainingConfig(
    trainSet = dummyData,
    testSet = Nil,
    features = List(dummyFeatures),
    hp = HyperParams(0.1, Regularization.None),
    epochs = 5,
    batchSize = 2,
    seed = Some(1234L)
  )

  case class TestActors(
                         gossipActor: ActorRef[GossipCommand],
                         rootProbe: TestProbe[RootCommand],
                         modelProbe: TestProbe[ModelCommand],
                         trainerProbe: TestProbe[TrainerCommand],
                         discoveryProbe: TestProbe[DiscoveryCommand]
                       )

  def setup(): TestActors = {
    val rootProbe = createTestProbe[RootCommand]()
    val modelProbe = createTestProbe[ModelCommand]()
    val trainerProbe = createTestProbe[TrainerCommand]()
    val discoveryProbe = createTestProbe[DiscoveryCommand]()

    val gossipActor = spawn(GossipActor(
      rootProbe.ref,
      modelProbe.ref,
      trainerProbe.ref,
      discoveryProbe.ref
    ))

    TestActors(gossipActor, rootProbe, modelProbe, trainerProbe, discoveryProbe)
  }

  test("GossipActor should register itself with DiscoveryActor upon startup") {
    val actors = setup()

    val msg = actors.discoveryProbe.expectMessageType[RegisterGossip]
    msg.gossip shouldBe actors.gossipActor
  }

  test("GossipActor should handle ShareConfig by fetching peers and broadcasting PrepareClient") {
    val actors = setup()

    actors.discoveryProbe.expectMessageType[RegisterGossip]

    actors.gossipActor ! GossipCommand.ShareConfig("seed-1", dummyModel, dummyConfig)

    val req = actors.discoveryProbe.expectMessageType[NodesRefRequest]

    val peer1 = createTestProbe[GossipCommand]()
    val peer2 = createTestProbe[GossipCommand]()

    req.replyTo ! List(peer1.ref, peer2.ref)

    val cmd1 = peer1.expectMessageType[GossipCommand.HandleControlCommand]
    cmd1.cmd shouldBe a[ControlCommand.PrepareClient]

    val cmd2 = peer2.expectMessageType[GossipCommand.HandleControlCommand]
    cmd2.cmd shouldBe a[ControlCommand.PrepareClient]
  }

  test("GossipActor should distribute dataset chunks to peers") {
    val actors = setup()
    actors.discoveryProbe.expectMessageType[RegisterGossip]

    actors.gossipActor ! GossipCommand.DistributeDataset(dummyData, Nil)

    val req = actors.discoveryProbe.expectMessageType[NodesRefRequest]

    val peer1 = createTestProbe[GossipCommand]()
    val peer2 = createTestProbe[GossipCommand]()
    req.replyTo ! List(peer1.ref, peer2.ref)

    val msg1 = peer1.expectMessageType[GossipCommand.HandleDistributeDataset]
    msg1.trainShard.size shouldBe 2

    val msg2 = peer2.expectMessageType[GossipCommand.HandleDistributeDataset]
    msg2.trainShard.size shouldBe 2

    msg1.trainShard should not be msg2.trainShard
  }

  test("GossipActor should handle TickGossip: fetch peers, get local model, and send to random peer") {
    val actors = setup()
    actors.discoveryProbe.expectMessageType[RegisterGossip]

    actors.gossipActor ! GossipCommand.ShareConfig("seed-1", dummyModel, dummyConfig)
    actors.discoveryProbe.expectMessageType[NodesRefRequest] // Ignora la request del ShareConfig

    actors.gossipActor ! GossipCommand.TickGossip

    val req = actors.discoveryProbe.expectMessageType[NodesRefRequest]

    val remotePeer = createTestProbe[GossipCommand]()

    req.replyTo ! List(remotePeer.ref)

    val modelReq = actors.modelProbe.expectMessageType[ModelCommand.GetModel]

    modelReq.replyTo ! dummyModel

    val remoteMsg = remotePeer.expectMessageType[GossipCommand.HandleRemoteModel]
    remoteMsg.remoteModel shouldBe dummyModel
  }

  test("GossipActor should request initial config if TickGossip happens before initialization") {
    val actors = setup()
    actors.discoveryProbe.expectMessageType[RegisterGossip]

    actors.gossipActor ! GossipCommand.TickGossip
    actors.discoveryProbe.expectMessageType[NodesRefRequest]

    val configReq = actors.discoveryProbe.expectMessageType[NodesRefRequest]

    val seedNode = createTestProbe[GossipCommand]()
    configReq.replyTo ! List(seedNode.ref)

    val askMsg = seedNode.expectMessageType[GossipCommand.RequestInitialConfig]
    askMsg.replyTo shouldBe actors.gossipActor
  }

  test("GossipActor should propagate ControlCommand (Pause/Resume) to local actors") {
    val actors = setup()
    actors.discoveryProbe.expectMessageType[RegisterGossip]

    actors.gossipActor ! GossipCommand.HandleControlCommand(ControlCommand.GlobalPause)
    actors.trainerProbe.expectMessage(TrainerCommand.Pause)

    actors.gossipActor ! GossipCommand.HandleControlCommand(ControlCommand.GlobalResume)
    actors.trainerProbe.expectMessage(TrainerCommand.Resume)
  }

  test("GossipActor should handle HandleRemoteModel by syncing with local ModelActor") {
    val actors = setup()
    actors.discoveryProbe.expectMessageType[RegisterGossip]

    actors.gossipActor ! GossipCommand.HandleRemoteModel(dummyModel)

    val syncMsg = actors.modelProbe.expectMessageType[ModelCommand.SyncModel]
    syncMsg.remoteModel shouldBe dummyModel
  }
}