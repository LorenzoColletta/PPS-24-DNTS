package actors.gossip

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.ActorRef
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers
import scala.concurrent.duration.*

import actors.gossip.consensus.{ConsensusActor, ConsensusProtocol}
import actors.gossip.consensus.ConsensusProtocol.*
import actors.model.ModelActor.ModelCommand
import actors.discovery.DiscoveryProtocol.{DiscoveryCommand, NodesRefRequest}
import domain.network.{Activations, Feature, ModelBuilder}
import config.{AppConfig, ProductionConfig}

class ConsensusActorTest extends ScalaTestWithActorTestKit with AnyFunSuiteLike with Matchers {

  given AppConfig = ProductionConfig

  private final val dummyFeatures = Feature.X

  private final val dummyModel = ModelBuilder.fromInputs(dummyFeatures)
    .addLayer(1, Activations.Sigmoid)
    .withSeed(1234L)
    .build()

  private def setup(): (ActorRef[ConsensusCommand], TestProbe[ModelCommand], TestProbe[DiscoveryCommand], TestProbe[ConsensusCommand]) = {
    val modelProbe = createTestProbe[ModelCommand]()
    val discoveryProbe = createTestProbe[DiscoveryCommand]()
    val peerProbe = createTestProbe[ConsensusCommand]() // simula un nodo peer remoto

    val consensusActor = spawn(ConsensusActor(
      modelProbe.ref,
      discoveryProbe.ref
    ))

    (consensusActor, modelProbe, discoveryProbe, peerProbe)
  }

  test("ConsensusActor should request nodes from DiscoveryActor upon TickConsensus") {
    val (consensus, _, discoveryProbe, _) = setup()

    consensus ! TickConsensus

    val msg = discoveryProbe.expectMessageType[NodesRefRequest]
    msg.replyTo shouldBe a[ActorRef[_]]
  }

  test("ConsensusActor should skip round if no remote peers are found (WrappedPeersForConsensus)") {
    val (consensus, modelProbe, _, _) = setup()

    consensus ! WrappedPeersForConsensus(List.empty)

    modelProbe.expectNoMessage(500.millis)
  }

  test("ConsensusActor should initiate consensus round upon WrappedLocalModelForConsensus") {
    val (consensus, _, _, peerProbe) = setup()

    val roundId = 1L

    consensus ! WrappedLocalModelForConsensus(dummyModel, List(peerProbe.ref), roundId)

    peerProbe.expectMessage(RequestModelForConsensus(consensus, roundId))
  }

  test("ConsensusActor should request local model when a peer asks for it (RequestModelForConsensus)") {
    val (consensus, modelProbe, _, peerProbe) = setup()

    val roundId = 2L
    consensus ! RequestModelForConsensus(peerProbe.ref, roundId)

    modelProbe.expectMessageType[ModelCommand.GetModel]
  }

  test("ConsensusActor should forward model reply to the requesting peer (ForwardModelReply)") {
    val (consensus, _, _, peerProbe) = setup()

    val roundId = 3L
    consensus ! ForwardModelReply(peerProbe.ref, dummyModel, roundId)

    peerProbe.expectMessage(ConsensusModelReply(dummyModel, roundId))
  }

  test("ConsensusActor should compute network consensus and update model when all expected peers reply") {
    val (consensus, modelProbe, _, peerProbe) = setup()
    val roundId = 1L

    consensus ! WrappedLocalModelForConsensus(dummyModel, List(peerProbe.ref), roundId)
    peerProbe.expectMessage(RequestModelForConsensus(consensus, roundId))

    val remoteModel = ModelBuilder.fromInputs(dummyFeatures)
      .addLayer(1, Activations.Sigmoid)
      .withSeed(999L)
      .build()

    consensus ! ConsensusModelReply(remoteModel, roundId)

    val updateMsg = modelProbe.expectMessageType[ModelCommand.UpdateConsensus]
    updateMsg.consensusValue should be >= 0.0
  }

  test("ConsensusActor should discard replies for a stale or non-existent round") {
    val (consensus, modelProbe, _, peerProbe) = setup()

    consensus ! WrappedLocalModelForConsensus(dummyModel, List(peerProbe.ref), 5L)
    peerProbe.expectMessage(RequestModelForConsensus(consensus, 5L))

    consensus ! ConsensusModelReply(dummyModel, 4L)

    modelProbe.expectNoMessage(500.millis)
  }

  test("ConsensusActor should compute partial consensus upon ConsensusRoundTimeout") {
    val (consensus, modelProbe, _, peerProbe) = setup()
    val roundId = 10L

    consensus ! WrappedLocalModelForConsensus(dummyModel, List(peerProbe.ref), roundId)
    peerProbe.expectMessage(RequestModelForConsensus(consensus, roundId))

    consensus ! ConsensusRoundTimeout(roundId)

    val updateMsg = modelProbe.expectMessageType[ModelCommand.UpdateConsensus]
    updateMsg.consensusValue shouldBe 0.0
  }

  test("ConsensusActor should stop gracefully upon receiving Stop command") {
    val (consensus, _, _, _) = setup()
    val deathProbe = createTestProbe[Any]()

    consensus ! ConsensusProtocol.Stop
    deathProbe.expectTerminated(consensus)
  }

}
