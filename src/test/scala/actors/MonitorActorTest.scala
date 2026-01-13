package actors

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.ActorRef
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers
import scala.concurrent.duration.*

import actors.MonitorActor.MonitorCommand
import actors.TrainerActor.{TrainerCommand, TrainingConfig}
import actors.ModelActor.ModelCommand
import domain.network.{HyperParams, Regularization, Feature}

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
    val monitor = spawn(MonitorActor(modelProbe.ref, trainerProbe.ref))

    monitor ! MonitorCommand.StartSimulation(dummyConfig)
    trainerProbe.expectMessage(TrainerCommand.Start(dummyConfig))

    modelProbe.expectMessageType[ModelCommand.GetMetrics]
  }

  test("MonitorActor should handle metrics response correctly") {
    val modelProbe = createTestProbe[ModelCommand]()
    val trainerProbe = createTestProbe[TrainerCommand]()
    val monitor = spawn(MonitorActor(modelProbe.ref, trainerProbe.ref))

    monitor ! MonitorCommand.StartSimulation(dummyConfig)
    val metricsRequest = modelProbe.expectMessageType[ModelCommand.GetMetrics]

    metricsRequest.replyTo ! MonitorCommand.MetricsResponse(loss = 0.5, consensus = 0.01)
    modelProbe.expectMessageType[ModelCommand.GetMetrics]
  }

  test("MonitorActor should stop the Trainer and clear the timers on Stop") {
    val modelProbe = createTestProbe[ModelCommand]()
    val trainerProbe = createTestProbe[TrainerCommand]()
    val monitor = spawn(MonitorActor(modelProbe.ref, trainerProbe.ref))

    monitor ! MonitorCommand.StartSimulation(dummyConfig)
    trainerProbe.expectMessage(TrainerCommand.Start(dummyConfig))

    monitor ! MonitorCommand.StopSimulation
    trainerProbe.expectMessage(TrainerCommand.Stop)

    modelProbe.expectNoMessage(1.second)
  }
}
