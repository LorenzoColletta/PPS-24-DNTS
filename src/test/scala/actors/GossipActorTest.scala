package actors

import actors.cluster.ClusterProtocol
import actors.cluster.ClusterProtocol.ClusterMemberCommand
import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers
import akka.actor.typed.ActorRef
import domain.network.{Activations, Feature, ModelBuilder}
import domain.data.{Label, LabeledPoint2D, Point2D}
import actors.gossip.GossipActor
import actors.gossip.GossipActor.{ControlCommand, GossipCommand}
import actors.model.ModelActor.ModelCommand
import actors.trainer.TrainerActor.TrainerCommand
import actors.discovery.DiscoveryProtocol.{DiscoveryCommand, NodesRefRequest, RegisterGossip}
import config.{AppConfig, ProductionConfig}

import scala.concurrent.duration.*

class GossipActorTest extends ScalaTestWithActorTestKit with AnyFunSuiteLike with Matchers {

  given AppConfig = ProductionConfig

  private final val dummyFeatures = Feature.X

  private final val dummyModel = ModelBuilder.fromInputs(dummyFeatures)
    .addLayer(1, Activations.Sigmoid)
    .build()

  private final val dummyData = List(
    LabeledPoint2D(Point2D(0.0, 0.0), Label.Negative),
    LabeledPoint2D(Point2D(1.0, 1.0), Label.Positive),
    LabeledPoint2D(Point2D(0.5, 0.5), Label.Negative),
    LabeledPoint2D(Point2D(0.2, 0.2), Label.Positive)
  )

  def setupGossip(): (ActorRef[GossipCommand], TestProbe[ModelCommand], TestProbe[TrainerCommand], TestProbe[ClusterCommand]) = {
    val modelProbe = createTestProbe[ModelCommand]()
    val trainerProbe = createTestProbe[TrainerCommand]()
    val clusterProbe = createTestProbe[ClusterMemberCommand]()
    val discoveryProbe = createTestProbe[DiscoveryCommand]()

    val gossipActor = spawn(GossipActor(
      modelProbe.ref,
      trainerProbe.ref,
      clusterProbe.ref,
      discoveryProbe.ref
    ))

<<<<<<< HEAD
    (gossipActor, modelProbe, trainerProbe, clusterProbe)
  }

  test("GossipActor should start periodic gossip when receiving StartGossipTick") {
    val (gossipActor, _, _, clusterProbe) = setupGossip()
=======
    (gossipActor, modelProbe, monitorProbe, trainerProbe, clusterProbe, discoveryProbe)
  }

  test("GossipActor should start periodic gossip when receiving StartGossipTick") {
    val (gossipActor, _, _, _, _, discoveryProbe) = setupGossip()

    discoveryProbe.expectMessageType[RegisterGossip]
>>>>>>> develop

    gossipActor ! GossipCommand.StartGossipTick

    discoveryProbe.expectMessageType[NodesRefRequest](5.seconds)
  }

  test("GossipActor should stop periodic gossip when receiving StopGossipTick") {
<<<<<<< HEAD
    val (gossipActor, _, _, clusterProbe) = setupGossip()
=======
    val (gossipActor, _, _, _, clusterProbe, discoveryProbe) = setupGossip()

    discoveryProbe.expectMessageType[RegisterGossip]
>>>>>>> develop

    gossipActor ! GossipCommand.StartGossipTick

    discoveryProbe.expectMessageType[NodesRefRequest]

    gossipActor ! GossipCommand.StopGossipTick

    clusterProbe.expectNoMessage(2.seconds)
  }

  test("GossipActor should initiate gossip cycle on Tick: Request Nodes -> Get Model -> Send to Peer") {
<<<<<<< HEAD
    val (gossipActor, modelProbe, _, clusterProbe) = setupGossip()
=======
    val (gossipActor, modelProbe, _, _, _, discoveryProbe) = setupGossip()
>>>>>>> develop

    val peerProbe = createTestProbe[GossipCommand]()

    discoveryProbe.expectMessageType[RegisterGossip]

    gossipActor ! GossipCommand.TickGossip

    val nodesReq = discoveryProbe.expectMessageType[NodesRefRequest]

    nodesReq.replyTo ! List(gossipActor, peerProbe.ref)

    val modelReq = modelProbe.expectMessageType[ModelCommand.GetModel]

    modelReq.replyTo ! dummyModel

    peerProbe.expectMessageType[GossipCommand.HandleRemoteModel]
  }

  test("GossipActor should propagate remote models to the local ModelActor for synchronization") {
<<<<<<< HEAD
    val (gossipActor, modelProbe, _, _) = setupGossip()
=======
    val (gossipActor, modelProbe, _, _, _, _) = setupGossip()
>>>>>>> develop

    gossipActor ! GossipCommand.HandleRemoteModel(dummyModel)

    val msg = modelProbe.expectMessageType[ModelCommand.SyncModel]
    msg.remoteModel shouldBe dummyModel
  }

  test("GossipActor should shard and distribute dataset to peers") {
<<<<<<< HEAD
    val (gossipActor, _, _, clusterProbe) = setupGossip()
=======
    val (gossipActor, _, _, _, _, discoveryProbe) = setupGossip()

    discoveryProbe.expectMessageType[RegisterGossip]
>>>>>>> develop

    val peer1 = createTestProbe[GossipCommand]()
    val peer2 = createTestProbe[GossipCommand]()

    gossipActor ! GossipCommand.DistributeDataset(dummyData, Nil)

    val req = discoveryProbe.expectMessageType[NodesRefRequest]
    req.replyTo ! List(peer1.ref, peer2.ref)

    val msg1 = peer1.expectMessageType[GossipCommand.HandleDistributeDataset]
    val msg2 = peer2.expectMessageType[GossipCommand.HandleDistributeDataset]

    msg1.trainShard.size shouldBe 2
    msg2.trainShard.size shouldBe 2

    (msg1.trainShard ++ msg2.trainShard).toSet shouldBe dummyData.toSet
  }

  test("GossipActor should start Trainer when receiving a dataset shard") {
<<<<<<< HEAD
    val (gossipActor, _, trainerProbe, _) = setupGossip()
=======
    val (gossipActor, _, _, trainerProbe, _, _) = setupGossip()
>>>>>>> develop

    val shard = dummyData.take(2)

    gossipActor ! GossipCommand.HandleDistributeDataset(shard, Nil)

    val msg = trainerProbe.expectMessageType[TrainerCommand.Start]
    msg.trainSet shouldBe shard
  }

  test("GossipActor should propagate ControlCommand (e.g., Pause) to peers") {
<<<<<<< HEAD
    val (gossipActor, _, _, clusterProbe) = setupGossip()
=======
    val (gossipActor, _, _, _, _, discoveryProbe) = setupGossip()

    discoveryProbe.expectMessageType[RegisterGossip]

>>>>>>> develop
    val peerProbe = createTestProbe[GossipCommand]()

    gossipActor ! GossipCommand.SpreadCommand(ControlCommand.GlobalPause)

    val req = discoveryProbe.expectMessageType[NodesRefRequest]
    req.replyTo ! List(gossipActor, peerProbe.ref)

    val msg = peerProbe.expectMessageType[GossipCommand.HandleControlCommand]
    msg.cmd shouldBe ControlCommand.GlobalPause
  }

  test("GossipActor should execute ControlCommand locally (Pause)") {
<<<<<<< HEAD
    val (gossipActor, _, trainerProbe, _) = setupGossip()
=======
    val (gossipActor, _, monitorProbe, trainerProbe, _, _) = setupGossip()
>>>>>>> develop

    gossipActor ! GossipCommand.HandleControlCommand(ControlCommand.GlobalPause)

    trainerProbe.expectMessageType[TrainerCommand.Pause.type]
  }

  test("GossipActor should execute ControlCommand locally (GlobalStart)") {
<<<<<<< HEAD
    val (gossipActor, _, _, clusterProbe) = setupGossip()

    gossipActor ! GossipCommand.HandleControlCommand(ControlCommand.GlobalStart)

    clusterProbe.expectMessageType[StartSimulation.type]
  }

  test("GossipActor should execute ControlCommand locally (Stop)") {
    val (gossipActor, _, trainerProbe, _) = setupGossip()
=======
    val (gossipActor, _, monitorProbe, _, clusterProbe, _) = setupGossip()

    gossipActor ! GossipCommand.HandleControlCommand(ControlCommand.GlobalStart)

    clusterProbe.expectMessageType[ClusterProtocol.StartSimulation.type ]
    monitorProbe.expectMessageType[MonitorCommand.StartSimulation.type]
  }

  test("GossipActor should execute ControlCommand locally (Stop)") {
    val (gossipActor, _, monitorProbe, trainerProbe, _, _) = setupGossip()
>>>>>>> develop

    gossipActor ! GossipCommand.HandleControlCommand(ControlCommand.GlobalStop)

    trainerProbe.expectMessageType[TrainerCommand.Stop.type]
  }
}