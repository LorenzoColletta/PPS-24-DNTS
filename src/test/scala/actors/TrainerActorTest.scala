package actors

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers
import scala.concurrent.duration.*

import domain.network.{Network, Layer, Activations, HyperParams, Regularization, Feature}
import domain.data.LinearAlgebra.{Matrix, Vector}
import domain.data.{LabeledPoint2D, Label, Point2D}
import domain.training.LossFunction
import domain.training.Strategies.Losses.mse

class TrainerActorTest extends ScalaTestWithActorTestKit with AnyFunSuiteLike with Matchers {

  private final val dummyLayer = Layer(
    Matrix.fill(1, 1)(0.5),
    Vector.fromList(List.fill(1)(0.1)),
    Activations.Sigmoid
  )
  private final val dummyNetwork = Network(List(dummyLayer))

  private final val dummyData = List(
    LabeledPoint2D(Point2D(0.0, 0.0), Label.Negative),
    LabeledPoint2D(Point2D(1.0, 1.0), Label.Positive)
  )

  private final val dummyFeatures = List(Feature.X)

  private final val dummyConfig = TrainingConfig(
    dataset = dummyData,
    features = dummyFeatures,
    hp = HyperParams(0.1, Regularization.None),
    epochs = 5,
    batchSize = 2,
    seed = Some(1234L)
  )


  test("TrainerActor should start the training loop upon Start command") {
    val modelProbe = createTestProbe[ModelCommand]()
    val trainer = spawn(TrainerActor(modelProbe.ref))

    trainer ! TrainerCommand.Start(dummyConfig)

    modelProbe.expectMessageType[ModelCommand.GetModel]
  }

  test("TrainerActor should perform a full training step: Start -> GetModel -> Reply -> ApplyGradients") {
    val modelProbe = createTestProbe[ModelCommand]()
    val trainer = spawn(TrainerActor(modelProbe.ref))

    trainer ! TrainerCommand.Start(dummyConfig)

    val askMsg = modelProbe.expectMessageType[ModelCommand.GetModel]
    askMsg.replyTo ! dummyNetwork

    val msg = modelProbe.expectMessageType[ModelCommand.ApplyGradients]

    msg.grads.layers should not be empty
  }

  test("TrainerActor should stop itself when receiving Stop command") {
    val modelProbe = createTestProbe[ModelCommand]()
    val trainer = spawn(TrainerActor(modelProbe.ref))

    trainer ! TrainerCommand.Start(dummyConfig)
    modelProbe.expectMessageType[ModelCommand.GetModel]

    trainer ! TrainerCommand.Stop

    modelProbe.expectTerminated(trainer)
  }

  test("TrainerActor should pause processing upon Pause command and resume upon Resume") {
    val modelProbe = createTestProbe[ModelCommand]()
    val trainer = spawn(TrainerActor(modelProbe.ref))

    trainer ! TrainerCommand.Start(dummyConfig)

    val askMsg = modelProbe.expectMessageType[ModelCommand.GetModel]
    askMsg.replyTo ! dummyNetwork
    modelProbe.expectMessageType[ModelCommand.ApplyGradients]

    trainer ! TrainerCommand.Pause

    modelProbe.expectNoMessage(500.millis)

    trainer ! TrainerCommand.Resume

    val msgAfterResume = modelProbe.expectMessageType[ModelCommand]

    msgAfterResume shouldBe a[ModelCommand]
  }
}
