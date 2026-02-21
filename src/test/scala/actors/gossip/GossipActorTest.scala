package actors.gossip

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.ActorRef
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.*
import actors.gossip.GossipProtocol.*
import actors.model.ModelActor.ModelCommand
import actors.trainer.TrainerActor.TrainerCommand
import actors.trainer.TrainerActor.TrainingConfig
import actors.discovery.DiscoveryProtocol.{DiscoveryCommand, NodesRefRequest, RegisterGossip}
import actors.root.RootActor.RootCommand
import actors.gossip.configuration.ConfigurationProtocol
import actors.gossip.consensus.ConsensusProtocol
import actors.gossip.dataset_distribution.DatasetDistributionProtocol
import domain.network.{Activations, Feature, ModelBuilder}
import config.{AppConfig, ProductionConfig}
import domain.network.HyperParams
import domain.network.Regularization



class GossipActorTest extends ScalaTestWithActorTestKit with AnyFunSuiteLike with Matchers {

  given AppConfig = ProductionConfig

  private val dummyModel = ModelBuilder.fromInputs(Feature.X)
    .addLayer(1, Activations.Sigmoid)
    .build()

  private def setup() = {
    val rootProbe = createTestProbe[RootCommand]()
    val modelProbe = createTestProbe[ModelCommand]()
    val trainerProbe = createTestProbe[TrainerCommand]()
    val discoveryProbe = createTestProbe[DiscoveryCommand]()
    val configProbe = createTestProbe[ConfigurationProtocol.ConfigurationCommand]()
    val distProbe = createTestProbe[DatasetDistributionProtocol.DatasetDistributionCommand]()
    val consensusProbe = createTestProbe[ConsensusProtocol.ConsensusCommand]()

    val gossipActor = spawn(GossipActor(
      rootProbe.ref,
      modelProbe.ref,
      trainerProbe.ref,
      discoveryProbe.ref,
      configProbe.ref,
      distProbe.ref,
      consensusProbe.ref
    ))

    discoveryProbe.expectMessageType[RegisterGossip]

    (gossipActor, rootProbe, modelProbe, trainerProbe, discoveryProbe, configProbe, distProbe, consensusProbe)
  }

  test("GossipActor should register itself with DiscoveryActor on startup") {
    setup()
  }

  test("GossipActor should route specialized commands to their respective actors") {
    val (gossip, _, _, _, _, configProbe, distProbe, consensusProbe) = setup()

    val configCmd = ConfigurationProtocol.RequestInitialConfig(createTestProbe[ConfigurationProtocol.ConfigurationCommand]().ref)
    gossip.unsafeUpcast[Any] ! configCmd
    configProbe.expectMessage(configCmd)

    val distCmd = DatasetDistributionProtocol.DistributeDataset(trainSet = Nil, testSet = Nil)
    gossip.unsafeUpcast[Any] ! distCmd
    distProbe.expectMessage(distCmd)

    val consCmd = ConsensusProtocol.StartTickConsensus
    gossip.unsafeUpcast[Any] ! consCmd
    consensusProbe.expectMessage(consCmd)
  }

  test("Gossip cycle: Tick -> NodesRefRequest -> GetModel -> SendModelToPeer") {
    val (gossip, _, modelProbe, _, discoveryProbe, _, _, _) = setup()
    val peerProbe = createTestProbe[GossipCommand]()

    gossip ! GossipCommand.StartGossipTick
    gossip ! GossipCommand.TickGossip

    val discoveryReq = discoveryProbe.expectMessageType[NodesRefRequest]
    discoveryReq.replyTo ! List(peerProbe.ref)

    val modelReq = modelProbe.expectMessageType[ModelCommand.GetModel]
    modelReq.replyTo ! dummyModel

    peerProbe.expectMessage(GossipCommand.HandleRemoteModel(dummyModel))
  }

  test("SpreadCommand should broadcast control signals to all peers") {
    val (gossip, _, _, _, discoveryProbe, _, _, _) = setup()
    val peer1 = createTestProbe[GossipCommand]()
    val peer2 = createTestProbe[GossipCommand]()

    gossip ! GossipCommand.SpreadCommand(ControlCommand.GlobalPause)

    val discoveryReq = discoveryProbe.expectMessageType[NodesRefRequest]
    discoveryReq.replyTo ! List(peer1.ref, peer2.ref)

    peer1.expectMessage(GossipCommand.HandleControlCommand(ControlCommand.GlobalPause))
    peer2.expectMessage(GossipCommand.HandleControlCommand(ControlCommand.GlobalPause))
  }

  test("GossipActor should handle remote model by syncing with local ModelActor") {
    val (gossip, _, modelProbe, _, _, _, _, _) = setup()

    gossip ! GossipCommand.HandleRemoteModel(dummyModel)

    modelProbe.expectMessage(ModelCommand.SyncModel(dummyModel))
  }

  test("GossipActor should execute received ControlCommands locally") {
    val (gossip, rootProbe, _, trainerProbe, _, _, _, _) = setup()

    gossip ! GossipCommand.HandleControlCommand(ControlCommand.GlobalPause)
    trainerProbe.expectMessage(TrainerCommand.Pause)

    gossip ! GossipCommand.HandleControlCommand(ControlCommand.GlobalResume)
    trainerProbe.expectMessage(TrainerCommand.Resume)

    val trainConfig = TrainingConfig(
      Nil,
      Nil,
      Nil,
      HyperParams(0.1, Regularization.None),
      1,
      1,
      None
    )
    gossip ! GossipCommand.HandleControlCommand(ControlCommand.PrepareClient("seed-1", dummyModel, trainConfig))
    rootProbe.expectMessage(RootCommand.ConfirmInitialConfiguration("seed-1", dummyModel, trainConfig))

    gossip ! GossipCommand.HandleControlCommand(ControlCommand.GlobalStop)
    rootProbe.expectMessage(RootCommand.StopSimulation)
  }

  test("GossipActor should stop polling when receiving StopGossipTick") {
    val (gossip, _, _, _, discoveryProbe, _, _, _) = setup()

    gossip ! GossipCommand.StartGossipTick
    gossip ! GossipCommand.StopGossipTick

    discoveryProbe.expectNoMessage(500.millis)
  }
}