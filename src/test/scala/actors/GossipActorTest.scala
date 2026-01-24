package actors

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.ActorRef
import org.scalatest.wordspec.AnyWordSpecLike
import domain.network.{Model, ModelBuilder, Feature}

import actors.gossip.GossipActor
import actors.gossip.GossipProtocol.*
import actors.gossip.GossipProtocol.ControlCommand.*
import actors.cluster.{ClusterCommand, NodesRefRequest, StartSimulation}
import actors.model.ModelActor.ModelCommand
import actors.monitor.MonitorActor.MonitorCommand
import actors.trainer.TrainerActor.TrainerCommand
import config.{AppConfig, ProductionConfig}

class GossipActorTest extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  given config: AppConfig = ProductionConfig

  val dummyModel: Model = ModelBuilder.fromInputs(Feature.X).build()

  "A GossipActor" should {

    "request peer list from ClusterManager on TickGossip" in {
      val modelProbe = createTestProbe[ModelCommand]()
      val monitorProbe = createTestProbe[MonitorCommand]()
      val trainerProbe = createTestProbe[TrainerCommand]()
      val clusterProbe = createTestProbe[ClusterCommand]()

      val gossip = spawn(GossipActor(
        modelProbe.ref,
        monitorProbe.ref,
        trainerProbe.ref,
        clusterProbe.ref
      ))

      gossip ! GossipCommand.TickGossip

      clusterProbe.expectMessageType[NodesRefRequest]
    }

    "request local model from ModelActor when peers are available" in {
      val modelProbe = createTestProbe[ModelCommand]()
      val monitorProbe = createTestProbe[MonitorCommand]()
      val trainerProbe = createTestProbe[TrainerCommand]()
      val clusterProbe = createTestProbe[ClusterCommand]()
      val peerProbe = createTestProbe[GossipCommand]()

      val gossip = spawn(GossipActor(
        modelProbe.ref,
        monitorProbe.ref,
        trainerProbe.ref,
        clusterProbe.ref
      ))

      gossip ! GossipCommand.WrappedPeers(List(peerProbe.ref))
      modelProbe.expectMessageType[ModelCommand.GetModel]
    }

    "not request local model if peer list is empty" in {
      val modelProbe = createTestProbe[ModelCommand]()
      val clusterProbe = createTestProbe[ClusterCommand]()

      val gossip = spawn(GossipActor(
        modelProbe.ref,
        createTestProbe[MonitorCommand]().ref,
        createTestProbe[TrainerCommand]().ref,
        clusterProbe.ref
      ))
      gossip ! GossipCommand.WrappedPeers(List.empty)

      modelProbe.expectNoMessage()
    }

    "forward local model to the selected target peer" in {
      val modelProbe = createTestProbe[ModelCommand]()
      val peerProbe = createTestProbe[GossipCommand]()

      val gossip = spawn(GossipActor(
        modelProbe.ref,
        createTestProbe[MonitorCommand]().ref,
        createTestProbe[TrainerCommand]().ref,
        createTestProbe[ClusterCommand]().ref
      ))

      gossip ! GossipCommand.SendModelToPeer(dummyModel, peerProbe.ref)

      peerProbe.expectMessage(GossipCommand.HandleRemoteModel(dummyModel))
    }

    "sync local model when receiving a remote model" in {
      val modelProbe = createTestProbe[ModelCommand]()

      val gossip = spawn(GossipActor(
        modelProbe.ref,
        createTestProbe[MonitorCommand]().ref,
        createTestProbe[TrainerCommand]().ref,
        createTestProbe[ClusterCommand]().ref
      ))

      gossip ! GossipCommand.HandleRemoteModel(dummyModel)

      modelProbe.expectMessage(ModelCommand.SyncModel(dummyModel))
    }

    "propagate GlobalStart control command correctly" in {
      val clusterProbe = createTestProbe[ClusterCommand]()
      val monitorProbe = createTestProbe[MonitorCommand]()

      val gossip = spawn(GossipActor(
        createTestProbe[ModelCommand]().ref,
        monitorProbe.ref,
        createTestProbe[TrainerCommand]().ref,
        clusterProbe.ref
      ))

      gossip ! GossipCommand.HandleControlCommand(GlobalStart)

      clusterProbe.expectMessage(StartSimulation)

      monitorProbe.expectMessage(MonitorCommand.StartSimulation)
    }

    "propagate GlobalPause control command correctly" in {
      val monitorProbe = createTestProbe[MonitorCommand]()
      val trainerProbe = createTestProbe[TrainerCommand]()

      val gossip = spawn(GossipActor(
        createTestProbe[ModelCommand]().ref,
        monitorProbe.ref,
        trainerProbe.ref,
        createTestProbe[ClusterCommand]().ref
      ))

      gossip ! GossipCommand.HandleControlCommand(GlobalPause)

      monitorProbe.expectMessage(MonitorCommand.InternalPause)
      trainerProbe.expectMessage(TrainerCommand.Pause)
    }

    "propagate GlobalStop control command correctly" in {
      val monitorProbe = createTestProbe[MonitorCommand]()
      val trainerProbe = createTestProbe[TrainerCommand]()

      val gossip = spawn(GossipActor(
        createTestProbe[ModelCommand]().ref,
        monitorProbe.ref,
        trainerProbe.ref,
        createTestProbe[ClusterCommand]().ref
      ))

      gossip ! GossipCommand.HandleControlCommand(GlobalStop)

      monitorProbe.expectMessage(MonitorCommand.InternalStop)
      trainerProbe.expectMessage(TrainerCommand.Stop)
    }
  }
}