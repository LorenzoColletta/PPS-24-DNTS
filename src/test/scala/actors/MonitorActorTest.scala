package actors

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.ActorRef
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.*
import actors.monitor.MonitorActor.MonitorCommand
import actors.trainer.TrainerActor.{TrainerCommand, TrainingConfig}
import actors.ModelActor.ModelCommand
import actors.GossipActor.GossipCommand
import actors.monitor.MonitorActor
import domain.network.{Feature, HyperParams, Regularization}

class MonitorActorTest extends ScalaTestWithActorTestKit with AnyFunSuiteLike with Matchers {

  private val dummyConfig = TrainingConfig(
    dataset = Nil,
    features = List(Feature.X),
    hp = HyperParams(0.1, Regularization.None),
    epochs = 1,
    batchSize = 1,
    seed = Some(1234L)
  )

  test("MonitorActor should start the Trainer and poll for metrics on Start") {
    val modelProbe = createTestProbe[ModelCommand]()
    val trainerProbe = createTestProbe[TrainerCommand]()
    val gossipProbe = createTestProbe[GossipCommand]()

    val monitor = spawn(MonitorActor(modelProbe.ref, trainerProbe.ref, gossipProbe.ref))

    monitor ! MonitorCommand.StartWithData(dummyConfig)

    modelProbe.expectMessageType[ModelCommand.GetMetrics]
  }

  test("MonitorActor should handle metrics response correctly") {
    val modelProbe = createTestProbe[ModelCommand]()
    val trainerProbe = createTestProbe[TrainerCommand]()
    val gossipProbe = createTestProbe[GossipCommand]()

    val monitor = spawn(MonitorActor(modelProbe.ref, trainerProbe.ref, gossipProbe.ref))

    monitor ! MonitorCommand.StartWithData(dummyConfig)
    val metricsRequest = modelProbe.expectMessageType[ModelCommand.GetMetrics]

    metricsRequest.replyTo ! MonitorCommand.MetricsResponse(trainLoss = 0.5, testLoss = 0.6, consensus = 0.01)
    modelProbe.expectMessageType[ModelCommand.GetMetrics]
  }

  test("MonitorActor should propagate Pause to Gossip") {
    val modelProbe = createTestProbe[ModelCommand]()
    val trainerProbe = createTestProbe[TrainerCommand]()
    val gossipProbe = createTestProbe[GossipCommand]()

    val monitor = spawn(MonitorActor(modelProbe.ref, trainerProbe.ref, gossipProbe.ref))

    monitor ! MonitorCommand.StartWithData(dummyConfig)
    monitor ! MonitorCommand.PauseSimulation

    gossipProbe.expectMessageType[GossipCommand.SpreadCommand]
  }

  test("MonitorActor should stop the Trainer and clear the timers on Stop") {
    val modelProbe = createTestProbe[ModelCommand]()
    val trainerProbe = createTestProbe[TrainerCommand]()
    val gossipProbe = createTestProbe[GossipCommand]()

    val monitor = spawn(MonitorActor(modelProbe.ref, trainerProbe.ref, gossipProbe.ref))

    monitor ! MonitorCommand.StartWithData(dummyConfig)
    monitor ! MonitorCommand.StopSimulation

    modelProbe.expectNoMessage(1.second)
  }
}
