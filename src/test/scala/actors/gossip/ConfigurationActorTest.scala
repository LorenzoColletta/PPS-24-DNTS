package actors

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.ActorRef
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers
import scala.concurrent.duration.*

import actors.gossip.configuration.{ConfigurationActor, ConfigurationProtocol}
import actors.discovery.DiscoveryProtocol
import actors.gossip.GossipProtocol.GossipCommand
import actors.gossip.GossipActor.ControlCommand
import actors.trainer.TrainerProtocol.TrainingConfig
import domain.network.{Activations, Feature, ModelBuilder}
import config.{AppConfig, ProductionConfig}

class ConfigurationActorTest extends ScalaTestWithActorTestKit with AnyFunSuiteLike with Matchers {

  given config: AppConfig = ProductionConfig

  private val dummyFeatures = List(Feature.X)

  private val dummyModel = ModelBuilder.fromInputs(Feature.X)
    .addLayer(1, Activations.Sigmoid)
    .build()

  private val dummyTrainConfig = TrainingConfig(
    trainSet = Nil, testSet = Nil, features = dummyFeatures,
    hp = null, epochs = 1, batchSize = 1, seed = None
  )

  private def setup() = {
    val discoveryProbe = createTestProbe[DiscoveryProtocol.DiscoveryCommand]()
    val gossipProbe = createTestProbe[GossipCommand]()
    val configActor = spawn(ConfigurationActor(discoveryProbe.ref))
    (configActor, discoveryProbe, gossipProbe)
  }

  test("ConfigurationActor should register Gossip actor and handle StartTickRequest") {
    val (configActor, discoveryProbe, _) = setup()

    configActor ! ConfigurationProtocol.StartTickRequest

    eventually {
      discoveryProbe.expectMessageType[DiscoveryProtocol.NodesRefRequest]
    }
  }

  test("ConfigurationActor should ask peers for config when receiving TickRequest and cache is empty") {
    val (configActor, discoveryProbe, gossipProbe) = setup()
    val peerProbe = createTestProbe[ConfigurationProtocol.ConfigurationCommand]()

    configActor ! ConfigurationProtocol.RegisterGossip(gossipProbe.ref)

    configActor ! ConfigurationProtocol.TickRequest

    val discoveryMsg = discoveryProbe.expectMessageType[DiscoveryProtocol.NodesRefRequest]

    discoveryMsg.replyTo ! List(peerProbe.ref.unsafeUpcast[GossipCommand])

    val request = peerProbe.expectMessageType[ConfigurationProtocol.RequestInitialConfig]
    request.replyTo shouldBe configActor
  }

  test("ConfigurationActor should not ask peers if Gossip actor is not yet registered") {
    val (configActor, discoveryProbe, _) = setup()

    val peerProbe = createTestProbe[GossipCommand]()

    configActor ! ConfigurationProtocol.TickRequest

    val discoveryMsg = discoveryProbe.expectMessageType[DiscoveryProtocol.NodesRefRequest]

    discoveryMsg.replyTo ! List(peerProbe.ref)

    peerProbe.expectNoMessage(200.millis)
  }

  test("ConfigurationActor should share its config when requested if it has one cached") {
    val (configActor, _, _) = setup()
    val requesterProbe = createTestProbe[ConfigurationProtocol.ConfigurationCommand]()

    configActor ! ConfigurationProtocol.ShareConfig("seed-1", dummyModel, dummyTrainConfig)
    configActor ! ConfigurationProtocol.RequestInitialConfig(requesterProbe.ref)

    requesterProbe.expectMessage(ConfigurationProtocol.ShareConfig("seed-1", dummyModel, dummyTrainConfig))
  }

  test("ConfigurationActor should notify Gossip actor (PrepareClient) when receiving a ShareConfig") {
    val (configActor, _, gossipProbe) = setup()

    configActor ! ConfigurationProtocol.RegisterGossip(gossipProbe.ref)
    configActor ! ConfigurationProtocol.ShareConfig("seed-99", dummyModel, dummyTrainConfig)

    val msg = gossipProbe.expectMessageType[GossipCommand.HandleControlCommand]
    msg.cmd shouldBe ControlCommand.PrepareClient("seed-99", dummyModel, dummyTrainConfig)
  }

  test("ConfigurationActor should stop polling when receiving StopTickRequest") {
    val (configActor, discoveryProbe, _) = setup()

    configActor ! ConfigurationProtocol.StartTickRequest
    configActor ! ConfigurationProtocol.StopTickRequest

    discoveryProbe.expectNoMessage(500.millis)
  }

  test("ConfigurationActor should stop itself when receiving Stop command") {
    val (configActor, _, _) = setup()
    val probe = createTestProbe()

    configActor ! ConfigurationProtocol.Stop

    probe.expectTerminated(configActor)
  }
}