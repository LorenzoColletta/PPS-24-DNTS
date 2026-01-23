package actors

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.*

import domain.network.{Activations, Feature, ModelBuilder, Regularization, HyperParams}
import domain.data.LinearAlgebra.{Matrix, Vector}
import domain.data.{Label, LabeledPoint2D, Point2D}
import domain.training.Strategies.Losses.mse
import actors.trainer.TrainerActor.{TrainerCommand, TrainingConfig}
import actors.model.ModelActor.ModelCommand
import actors.trainer.TrainerActor
import config.{AppConfig, ProductionConfig}

class TrainerActorTest extends ScalaTestWithActorTestKit with AnyFunSuiteLike with Matchers {

  given AppConfig = ProductionConfig

  private final val dummyFeatures = Feature.X
  
  private final val dummyModel = ModelBuilder.fromInputs(dummyFeatures)
    .addLayer(neurons = 1, activation = Activations.Sigmoid)
    .withSeed(1234L)
    .build()

  private final val dummyData = List(
    LabeledPoint2D(Point2D(0.0, 0.0), Label.Negative),
    LabeledPoint2D(Point2D(1.0, 1.0), Label.Positive)
  )

  private final val dummyConfig = TrainingConfig(
    trainSet = dummyData,
    testSet = Nil,
    features = List(dummyFeatures),
    hp = HyperParams(0.1, Regularization.None),
    epochs = 5,
    batchSize = 2,
    seed = Some(1234L)
  )


  test("TrainerActor should start the training loop upon Start command") {
    val modelProbe = createTestProbe[ModelCommand]()
    val trainer = spawn(TrainerActor(modelProbe.ref))

    trainer ! TrainerCommand.SetTrainConfig(dummyConfig)
    trainer ! TrainerCommand.Start(dummyData, Nil)
    
    modelProbe.expectMessageType[ModelCommand.GetModel]
  }

  test("TrainerActor should perform a full training step: Start -> GetModel -> Reply -> ApplyGradients") {
    val modelProbe = createTestProbe[ModelCommand]()
    val trainer = spawn(TrainerActor(modelProbe.ref))

    trainer ! TrainerCommand.SetTrainConfig(dummyConfig)
    trainer ! TrainerCommand.Start(dummyData, Nil)

    val askMsg = modelProbe.expectMessageType[ModelCommand.GetModel]
    askMsg.replyTo ! dummyModel

    val msg = modelProbe.expectMessageType[ModelCommand.ApplyGradients]

    msg.grads.layers should not be empty
  }

  test("TrainerActor should stop itself when receiving Stop command") {
    val modelProbe = createTestProbe[ModelCommand]()
    val trainer = spawn(TrainerActor(modelProbe.ref))

    trainer ! TrainerCommand.SetTrainConfig(dummyConfig)
    trainer ! TrainerCommand.Start(dummyData, Nil)
    
    modelProbe.expectMessageType[ModelCommand.GetModel]

    trainer ! TrainerCommand.Stop

    modelProbe.expectTerminated(trainer)
  }

  test("TrainerActor should pause processing upon Pause command and resume upon Resume") {
    val modelProbe = createTestProbe[ModelCommand]()
    val trainer = spawn(TrainerActor(modelProbe.ref))

    trainer ! TrainerCommand.SetTrainConfig(dummyConfig)
    trainer ! TrainerCommand.Start(dummyData, Nil)

    val askMsg = modelProbe.expectMessageType[ModelCommand.GetModel]
    askMsg.replyTo ! dummyModel
    modelProbe.expectMessageType[ModelCommand.ApplyGradients]

    trainer ! TrainerCommand.Pause

    modelProbe.expectNoMessage(500.millis)

    trainer ! TrainerCommand.Resume

    val msgAfterResume = modelProbe.expectMessageType[ModelCommand]

    msgAfterResume shouldBe a[ModelCommand]
  }
}
