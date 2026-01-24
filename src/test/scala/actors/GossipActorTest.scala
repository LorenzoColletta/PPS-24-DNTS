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
import domain.serialization.Serializer
import domain.serialization.ControlCommandSerializers.given
import domain.serialization.ModelSerializers.given
import domain.serialization.NetworkSerializers.given
import domain.serialization.LinearAlgebraSerializers.given
import domain.network.Activations.given

class GossipActorTest extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  given config: AppConfig = ProductionConfig
  val dummyModel: Model = ModelBuilder.fromInputs(Feature.X).build()

  "A GossipActor" should {

    "request peer list from ClusterManager on TickGossip" in {
      val clusterProbe = createTestProbe[ClusterCommand]()
      val gossip = spawn(GossipActor(
        createTestProbe[ModelCommand]().ref,
        createTestProbe[MonitorCommand]().ref,
        createTestProbe[TrainerCommand]().ref,
        clusterProbe.ref
      ))

      gossip ! GossipCommand.TickGossip
      clusterProbe.expectMessageType[NodesRefRequest]
    }

    "spread a control command to other peers when receiving SpreadCommand" in {
      val clusterProbe = createTestProbe[ClusterCommand]()
      val peerProbe = createTestProbe[GossipCommand]()

      val gossip = spawn(GossipActor(
        createTestProbe[ModelCommand]().ref,
        createTestProbe[MonitorCommand]().ref,
        createTestProbe[TrainerCommand]().ref,
        clusterProbe.ref
      ))

      gossip ! GossipCommand.SpreadCommand(GlobalPause)

      val request = clusterProbe.expectMessageType[NodesRefRequest]

      request.replyTo ! List(peerProbe.ref, gossip.ref)

      val received = peerProbe.expectMessageType[GossipCommand.HandleControlCommand]
      val deserialized = summon[Serializer[ControlCommand]].deserialize(received.bytes).get

      deserialized shouldBe GlobalPause
    }

    "execute local actions when receiving a serialized HandleControlCommand" in {
      val monitorProbe = createTestProbe[MonitorCommand]()
      val trainerProbe = createTestProbe[TrainerCommand]()
      val serializer = summon[Serializer[ControlCommand]]

      val gossip = spawn(GossipActor(
        createTestProbe[ModelCommand]().ref,
        monitorProbe.ref,
        trainerProbe.ref,
        createTestProbe[ClusterCommand]().ref
      ))

      val bytes = serializer.serialize(GlobalStop)
      gossip ! GossipCommand.HandleControlCommand(bytes)

      monitorProbe.expectMessage(MonitorCommand.InternalStop)
      trainerProbe.expectMessage(TrainerCommand.Stop)
    }

    "sync local model when receiving a serialized HandleRemoteModel" in {
      val modelProbe = createTestProbe[ModelCommand]()
      val modelSerializer = summon[Serializer[Model]]

      val gossip = spawn(GossipActor(
        modelProbe.ref,
        createTestProbe[MonitorCommand]().ref,
        createTestProbe[TrainerCommand]().ref,
        createTestProbe[ClusterCommand]().ref
      ))

      val bytes = modelSerializer.serialize(dummyModel)
      gossip ! GossipCommand.HandleRemoteModel(bytes)

      val syncMsg = modelProbe.expectMessageType[ModelCommand.SyncModel]
      syncMsg.remoteModel.features shouldBe dummyModel.features
    }
  }
}