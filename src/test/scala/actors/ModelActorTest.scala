package actors

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.ActorRef
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers
import actors.model.ModelActor
import actors.model.ModelActor.ModelCommand
import actors.trainer.TrainerActor.TrainerCommand
import actors.monitor.MonitorActor.MonitorCommand
import domain.network.{Activations, Feature, Model, ModelBuilder}
import domain.training.Strategies.Optimizers.SGD
import domain.training.Strategies.Regularizers
import domain.network.Regularization
import domain.training.LayerGradient
import domain.data.Point2D
import domain.data.LinearAlgebra.{Matrix, Vector}
import config.{AppConfig, ProductionConfig}

class ModelActorTest extends ScalaTestWithActorTestKit with AnyFunSuiteLike with Matchers {

  given AppConfig = ProductionConfig

  private final val dummyFeatures = Feature.X
  private final val dummyModel = ModelBuilder.fromInputs(dummyFeatures)
    .addLayer(1, Activations.Sigmoid)
    .build()

  private final val dummyReg = Regularizers.fromConfig(Regularization.None)
  private final val testOptimizer = new SGD(learningRate = 0.01, reg = dummyReg)


  def setup(): (ActorRef[ModelCommand], TestProbe[TrainerCommand]) = {
    val trainerProbe = createTestProbe[TrainerCommand]()
    val modelActor = spawn(ModelActor())
    (modelActor, trainerProbe)
  }

  test("ModelActor should start in idle and transition to active upon Initialize") {
    val (modelActor, trainerProbe) = setup()
    val replyProbe = createTestProbe[Model]()

    modelActor ! ModelCommand.Initialize(dummyModel, testOptimizer, trainerProbe.ref)

    modelActor ! ModelCommand.GetModel(replyProbe.ref)
    replyProbe.expectMessage(dummyModel)
  }

  test("ModelActor should apply gradients and update the model") {
    val (modelActor, trainerProbe) = setup()
    modelActor ! ModelCommand.Initialize(dummyModel, testOptimizer, trainerProbe.ref)

    val layerGradients = dummyModel.network.layers.map: layer =>
      val rows = layer.weights.rows
      val cols = layer.weights.cols
      val biasSize = layer.biases.length

      val weightGradients = Matrix.fill(rows, cols)(0.1)
      val biasGradients = Vector.fromList(List.fill(biasSize)(0.1))

      LayerGradient(weightGradients, biasGradients)

    val grads = domain.training.NetworkGradient(layerGradients)

    modelActor ! ModelCommand.ApplyGradients(grads)

    val replyProbe = createTestProbe[Model]()
    modelActor ! ModelCommand.GetModel(replyProbe.ref)
    val updatedModel = replyProbe.receiveMessage()

    updatedModel.network.layers.head.weights.toFlatList should not be dummyModel.network.layers.head.weights.toFlatList
  }

  test("ModelActor should handle SyncModel from Gossip") {
    val (modelActor, trainerProbe) = setup()
    modelActor ! ModelCommand.Initialize(dummyModel, testOptimizer, trainerProbe.ref)

    val remoteModel = ModelBuilder.fromInputs(dummyFeatures)
      .addLayer(1, Activations.Sigmoid)
      .withSeed(999L)
      .build()

    modelActor ! ModelCommand.SyncModel(remoteModel)

    val replyProbe = createTestProbe[Model]()
    modelActor ! ModelCommand.GetModel(replyProbe.ref)
    val currentModel = replyProbe.receiveMessage()

    currentModel should not be dummyModel
  }

  test("ModelActor should forward GetMetrics request to TrainerActor") {
    val (modelActor, trainerProbe) = setup()
    val monitorProbe = createTestProbe[MonitorCommand.ViewUpdateResponse]()

    modelActor ! ModelCommand.Initialize(dummyModel, testOptimizer, trainerProbe.ref)

    modelActor ! ModelCommand.GetMetrics(monitorProbe.ref)

    val msg = trainerProbe.expectMessageType[TrainerCommand.CalculateMetrics]
    msg.model shouldBe dummyModel
  }

  test("ModelActor should provide predictions for specific points") {
    val (modelActor, trainerProbe) = setup()
    val replyProbe = createTestProbe[Double]()

    modelActor ! ModelCommand.Initialize(dummyModel, testOptimizer, trainerProbe.ref)

    val testPoint = Point2D(0.5, 0.5)
    modelActor ! ModelCommand.GetPrediction(testPoint, replyProbe.ref)

    val prediction = replyProbe.receiveMessage()
    prediction should (be >= 0.0 and be <= 1.0) // Sigmoid output range
  }
}