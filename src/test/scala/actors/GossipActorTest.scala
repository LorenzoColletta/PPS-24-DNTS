package actors

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers
import akka.actor.typed.ActorRef
import domain.network.{Activations, Feature, ModelBuilder}
import domain.data.{Label, LabeledPoint2D, Point2D}
import actors.gossip.GossipActor
import actors.gossip.GossipActor.{ControlCommand, GossipCommand}
import actors.model.ModelActor.ModelCommand
import actors.monitor.MonitorActor.MonitorCommand
import actors.trainer.TrainerActor.TrainerCommand
import actors.cluster.{ClusterCommand, NodesRefRequest, StartSimulation}
import config.{AppConfig, ProductionConfig}

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

  def setupGossip(): (ActorRef[GossipCommand], TestProbe[ModelCommand], TestProbe[MonitorCommand], TestProbe[TrainerCommand], TestProbe[ClusterCommand]) = {
    val modelProbe = createTestProbe[ModelCommand]()
    val monitorProbe = createTestProbe[MonitorCommand]()
    val trainerProbe = createTestProbe[TrainerCommand]()
    val clusterProbe = createTestProbe[ClusterCommand]()

    val gossipActor = spawn(GossipActor(
      modelProbe.ref,
      monitorProbe.ref,
      trainerProbe.ref,
      clusterProbe.ref
    ))

    (gossipActor, modelProbe, monitorProbe, trainerProbe, clusterProbe)
  }

  test("GossipActor should initiate gossip cycle on Tick: Request Nodes -> Get Model -> Send to Peer") {
    val (gossipActor, modelProbe, _, _, clusterProbe) = setupGossip()

    val peerProbe = createTestProbe[GossipCommand]()

    gossipActor ! GossipCommand.TickGossip

    val nodesReq = clusterProbe.expectMessageType[NodesRefRequest]

    nodesReq.replyTo ! List(gossipActor, peerProbe.ref)

    val modelReq = modelProbe.expectMessageType[ModelCommand.GetModel]

    modelReq.replyTo ! dummyModel

    peerProbe.expectMessageType[GossipCommand.HandleRemoteModel]
  }

  test("GossipActor should forward received remote models to ModelActor for Sync") {
    val (gossipActor, modelProbe, _, _, _) = setupGossip()

    gossipActor ! GossipCommand.HandleRemoteModel(dummyModel)

    val msg = modelProbe.expectMessageType[ModelCommand.SyncModel]
    msg.remoteModel shouldBe dummyModel
  }

  test("GossipActor should shard and distribute dataset to peers") {
    val (gossipActor, _, _, _, clusterProbe) = setupGossip()

    val peer1 = createTestProbe[GossipCommand]()
    val peer2 = createTestProbe[GossipCommand]()

    gossipActor ! GossipCommand.DistributeDataset(dummyData, Nil)

    val req = clusterProbe.expectMessageType[NodesRefRequest]
    req.replyTo ! List(peer1.ref, peer2.ref)

    val msg1 = peer1.expectMessageType[GossipCommand.HandleDistributeDataset]
    val msg2 = peer2.expectMessageType[GossipCommand.HandleDistributeDataset]

    msg1.trainShard.size shouldBe 2
    msg2.trainShard.size shouldBe 2

    (msg1.trainShard ++ msg2.trainShard).toSet shouldBe dummyData.toSet
  }

  test("GossipActor should start Trainer when receiving a dataset shard") {
    val (gossipActor, _, _, trainerProbe, _) = setupGossip()

    val shard = dummyData.take(2)

    gossipActor ! GossipCommand.HandleDistributeDataset(shard, Nil)

    val msg = trainerProbe.expectMessageType[TrainerCommand.Start]
    msg.trainSet shouldBe shard
  }

  test("GossipActor should propagate ControlCommand (e.g., Pause) to peers") {
    val (gossipActor, _, _, _, clusterProbe) = setupGossip()
    val peerProbe = createTestProbe[GossipCommand]()

    gossipActor ! GossipCommand.SpreadCommand(ControlCommand.GlobalPause)

    val req = clusterProbe.expectMessageType[NodesRefRequest]
    req.replyTo ! List(gossipActor, peerProbe.ref)

    val msg = peerProbe.expectMessageType[GossipCommand.HandleControlCommand]
    msg.cmd shouldBe ControlCommand.GlobalPause
  }

  test("GossipActor should execute ControlCommand locally (Pause)") {
    val (gossipActor, _, monitorProbe, trainerProbe, _) = setupGossip()

    gossipActor ! GossipCommand.HandleControlCommand(ControlCommand.GlobalPause)

    monitorProbe.expectMessageType[MonitorCommand.InternalPause.type]
    trainerProbe.expectMessageType[TrainerCommand.Pause.type]
  }

  test("GossipActor should execute ControlCommand locally (GlobalStart)") {
    val (gossipActor, _, monitorProbe, _, clusterProbe) = setupGossip()

    gossipActor ! GossipCommand.HandleControlCommand(ControlCommand.GlobalStart)

    clusterProbe.expectMessageType[StartSimulation.type]
    monitorProbe.expectMessageType[MonitorCommand.StartSimulation.type]
  }

  test("GossipActor should execute ControlCommand locally (Stop)") {
    val (gossipActor, _, monitorProbe, trainerProbe, _) = setupGossip()

    gossipActor ! GossipCommand.HandleControlCommand(ControlCommand.GlobalStop)

    monitorProbe.expectMessageType[MonitorCommand.InternalStop.type]
    trainerProbe.expectMessageType[TrainerCommand.Stop.type]
  }
}