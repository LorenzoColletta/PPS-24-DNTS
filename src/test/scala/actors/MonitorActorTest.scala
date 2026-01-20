package actors

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.ActorRef
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.*
import actors.monitor.MonitorActor.MonitorCommand
import actors.trainer.TrainerActor.{TrainerCommand, TrainingConfig}
import actors.ModelActor.ModelCommand
import .GossipCommand
import actors.monitor.MonitorActor
import domain.network.{Feature, HyperParams, Regularization, Model, Network}
import view.{ViewBoundary, ViewStateSnapshot}
import config.{AppConfig, ProductionConfig}

class MonitorActorTest extends ScalaTestWithActorTestKit with AnyFunSuiteLike with Matchers {

  given AppConfig = ProductionConfig

  private final val dummyBoundary = new ViewBoundary:
    override def showInitialScreen(snapshot: ViewStateSnapshot, isMaster: Boolean): Unit = ()
    override def showInitialError(reason: String): Unit = ()
    override def startSimulation(snapshot: ViewStateSnapshot): Unit = ()
    override def updatePeerDisplay(active: Int, total: Int): Unit = ()
    override def plotMetrics(epoch: Int, trainLoss: Double, testLoss: Double, consensus: Double): Unit = ()
    override def plotDecisionBoundary(model: Model): Unit = ()
    override def setPausedState(paused: Boolean): Unit = ()
    override def showCrashMessage(): Unit = ()
    override def stopSimulation(): Unit = ()

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

    val monitor = spawn(MonitorActor(
      modelProbe.ref,
      trainerProbe.ref,
      gossipProbe.ref,
      dummyBoundary,
    ))

    monitor ! MonitorCommand.StartWithData(dummyConfig)

    modelProbe.expectMessageType[ModelCommand.GetMetrics]
  }

  test("MonitorActor should handle metrics response correctly") {
    val modelProbe = createTestProbe[ModelCommand]()
    val trainerProbe = createTestProbe[TrainerCommand]()
    val gossipProbe = createTestProbe[GossipCommand]()

    val monitor = spawn(MonitorActor(
      modelProbe.ref,
      trainerProbe.ref,
      gossipProbe.ref,
      dummyBoundary,
    ))

    monitor ! MonitorCommand.StartWithData(dummyConfig)
    val metricsRequest = modelProbe.expectMessageType[ModelCommand.GetMetrics]

    metricsRequest.replyTo !
      MonitorCommand.ViewUpdateResponse(
        epoch = 1,
        model = Model(Network(List.empty), List.empty),
        trainLoss = 0.5,
        testLoss = 0.6,
        consensus = 0.01
      )
    modelProbe.expectMessageType[ModelCommand.GetMetrics]
  }

  test("MonitorActor should propagate Pause to Gossip") {
    val modelProbe = createTestProbe[ModelCommand]()
    val trainerProbe = createTestProbe[TrainerCommand]()
    val gossipProbe = createTestProbe[GossipCommand]()

    val monitor = spawn(MonitorActor(
      modelProbe.ref,
      trainerProbe.ref,
      gossipProbe.ref,
      dummyBoundary,
    ))

    monitor ! MonitorCommand.StartWithData(dummyConfig)
    monitor ! MonitorCommand.PauseSimulation

    gossipProbe.expectMessageType[GossipCommand.SpreadCommand]
  }

  test("MonitorActor should stop the Trainer and clear the timers on Stop") {
    val modelProbe = createTestProbe[ModelCommand]()
    val trainerProbe = createTestProbe[TrainerCommand]()
    val gossipProbe = createTestProbe[GossipCommand]()

    val monitor = spawn(MonitorActor(
      modelProbe.ref,
      trainerProbe.ref,
      gossipProbe.ref,
      dummyBoundary,
    ))

    monitor ! MonitorCommand.StartWithData(dummyConfig)
    monitor ! MonitorCommand.InternalStop

    modelProbe.expectNoMessage(1.second)
  }
}
