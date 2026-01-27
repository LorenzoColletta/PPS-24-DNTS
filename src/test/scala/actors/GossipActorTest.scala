package actors

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.ActorRef
import org.scalatest.wordspec.AnyWordSpecLike
import actors.model.ModelActor.ModelCommand
import actors.cluster.{ClusterCommand, NodesRefRequest, StartSimulation}
import actors.gossip.GossipActor
import actors.monitor.MonitorActor.MonitorCommand
import actors.trainer.TrainerActor.TrainerCommand
import actors.gossip.GossipActor.{ControlCommand, GossipCommand}
import domain.network.{Activations, Feature, Model, ModelBuilder}
import config.{AppConfig, ProductionConfig}

class GossipActorTest extends ScalaTestWithActorTestKit with AnyWordSpecLike:

  private val dummyModel = ModelBuilder.fromInputs(Feature.X).addLayer(1, Activations.Relu).build()

  "A GossipActor (GossipBehavior)" should {

    val modelProbe = createTestProbe[ModelCommand]()
    val monitorProbe = createTestProbe[MonitorCommand]()
    val trainerProbe = createTestProbe[TrainerCommand]()
    val clusterProbe = createTestProbe[ClusterCommand]()

    given AppConfig = ProductionConfig

    val gossipActor = spawn(GossipActor(
      modelProbe.ref,
      monitorProbe.ref,
      trainerProbe.ref,
      clusterProbe.ref
    ))

    "request peers from cluster manager on TickGossip" in {
      gossipActor ! GossipCommand.TickGossip

      val request = clusterProbe.expectMessageType[NodesRefRequest]

      request.replyTo ! List(gossipActor.ref)
    }

    "sync model when receiving a remote model" in {
      gossipActor ! GossipCommand.HandleRemoteModel(dummyModel)

      modelProbe.expectMessage(ModelCommand.SyncModel(dummyModel))
    }

    "propagate control commands to other peers via SpreadCommand" in {
      val remotePeerProbe = createTestProbe[GossipCommand]()
      val command = ControlCommand.GlobalStart

      gossipActor ! GossipCommand.SpreadCommand(command)

      val request = clusterProbe.expectMessageType[NodesRefRequest]
      request.replyTo ! (List(gossipActor.ref, remotePeerProbe.ref))

      remotePeerProbe.expectMessage(GossipCommand.HandleControlCommand(command))
    }

    "handle local execution of ControlCommands (e.g., GlobalPause)" in {
      gossipActor ! GossipCommand.HandleControlCommand(ControlCommand.GlobalPause)

      monitorProbe.expectMessage(MonitorCommand.InternalPause)
      trainerProbe.expectMessage(TrainerCommand.Pause)
    }

    "handle local execution of ControlCommands (e.g., GlobalStart)" in {
      gossipActor ! GossipCommand.HandleControlCommand(ControlCommand.GlobalStart)

      clusterProbe.expectMessage(StartSimulation)
      monitorProbe.expectMessage(MonitorCommand.StartSimulation)
    }
  }