package actors

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.ActorRef
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.*
import actors.monitor.MonitorActor.MonitorCommand
import actors.trainer.TrainerActor.{TrainerCommand, TrainingConfig}
import actors.model.ModelActor.ModelCommand
import actors.gossip.GossipActor.GossipCommand
import actors.root.RootActor.RootCommand
import actors.monitor.MonitorActor
import domain.network.{Feature, HyperParams, Activations, Regularization, Model, ModelBuilder}
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

  private final val dummyFeatures = Feature.X
  
  private val dummyModel = ModelBuilder.fromInputs(dummyFeatures)
    .addLayer(1, Activations.Sigmoid)
    .build()

  private val dummyConfig = TrainingConfig(
    trainSet = Nil,
    testSet = Nil,
    features = List(dummyFeatures),
    hp = HyperParams(0.1, Regularization.None),
    epochs = 1,
    batchSize = 1,
    seed = Some(1234L)
  )

  test("MonitorActor should start the Trainer and poll for metrics on Start") {
    val modelProbe = createTestProbe[ModelCommand]()
    val trainerProbe = createTestProbe[TrainerCommand]()
    val gossipProbe = createTestProbe[GossipCommand]()
    val rootProbe = createTestProbe[RootCommand]()

    val monitor = spawn(MonitorActor(
      modelProbe.ref,
      trainerProbe.ref,
      gossipProbe.ref,
      rootProbe.ref,
      dummyBoundary,
    ))

    monitor ! MonitorCommand.Initialize("seed-node", dummyModel, dummyConfig)

    monitor ! MonitorCommand.StartWithData(dummyConfig.trainSet, dummyConfig.testSet)

    modelProbe.expectMessageType[ModelCommand.GetMetrics]
  }

  test("MonitorActor should handle metrics response correctly") {
    val modelProbe = createTestProbe[ModelCommand]()
    val trainerProbe = createTestProbe[TrainerCommand]()
    val gossipProbe = createTestProbe[GossipCommand]()
    val rootProbe = createTestProbe[RootCommand]()

    val monitor = spawn(MonitorActor(
      modelProbe.ref,
      trainerProbe.ref,
      gossipProbe.ref,
      rootProbe.ref,
      dummyBoundary,
    ))

    monitor ! MonitorCommand.Initialize("seed-node", dummyModel, dummyConfig)
    monitor ! MonitorCommand.StartWithData(dummyConfig.trainSet, dummyConfig.testSet)
    
    val metricsRequest = modelProbe.expectMessageType[ModelCommand.GetMetrics]

    metricsRequest.replyTo !
      MonitorCommand.ViewUpdateResponse(
        epoch = 1,
        model = dummyModel,
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
    val rootProbe = createTestProbe[RootCommand]()

    val monitor = spawn(MonitorActor(
      modelProbe.ref,
      trainerProbe.ref,
      gossipProbe.ref,
      rootProbe.ref,
      dummyBoundary,
    ))

    monitor ! MonitorCommand.Initialize("seed-node", dummyModel, dummyConfig)
    monitor ! MonitorCommand.StartWithData(dummyConfig.trainSet, dummyConfig.testSet)
    
    monitor ! MonitorCommand.PauseSimulation

    gossipProbe.expectMessageType[GossipCommand.SpreadCommand]
  }

  test("MonitorActor should clear the timers on Stop") {
    val modelProbe = createTestProbe[ModelCommand]()
    val trainerProbe = createTestProbe[TrainerCommand]()
    val gossipProbe = createTestProbe[GossipCommand]()
    val rootProbe = createTestProbe[RootCommand]()

    val monitor = spawn(MonitorActor(
      modelProbe.ref,
      trainerProbe.ref,
      gossipProbe.ref,
      rootProbe.ref,
      dummyBoundary,
    ))

    monitor ! MonitorCommand.Initialize("seed-node", dummyModel, dummyConfig)
    monitor ! MonitorCommand.StartWithData(dummyConfig.trainSet, dummyConfig.testSet)
    
    monitor ! MonitorCommand.InternalStop

    modelProbe.expectNoMessage(1.second)
  }
}
