package actors

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import domain.data.Point2D
import domain.network.{Activations, Feature, Model, ModelBuilder, Regularization}
import org.scalatest.wordspec.AnyWordSpecLike
import actors.model.ModelActor
import actors.model.ModelActor.ModelCommand
import domain.training.Strategies.Optimizers.SGD
import domain.training.Strategies.Regularizers

class ModelActorTest extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  val dummyReg = Regularizers.fromConfig(Regularization.None)
  val testOptimizer = new SGD(learningRate = 0.01, reg = dummyReg)

  def createModel(seed: Long): Model =
    ModelBuilder.fromInputs(Feature.X, Feature.Y)
      .addLayer(2, Activations.Relu)
      .withSeed(seed)
      .build()

  "A ModelActor" should {

    "return different predictions after a model update" in {
      val model1 = createModel(1L)
      val model2 = createModel(999L)

      val modelActor = spawn(ModelActor())

      modelActor ! ModelCommand.Initialize(model1, testOptimizer)

      val probe = createTestProbe[Double]()
      val testPoint = Point2D(0.5, 0.5)

      modelActor ! ModelCommand.GetPrediction(testPoint, probe.ref)
      val prediction1 = probe.receiveMessage()

      modelActor ! ModelCommand.TrainingCompleted(model2)
      modelActor ! ModelCommand.GetPrediction(testPoint, probe.ref)

      val prediction2 = probe.receiveMessage()

      prediction1 should not be prediction2
    }

    "return same predictions with same model" in {
      val model1 = createModel(1L)
      val model2 = createModel(1L)

      val modelActor = spawn(ModelActor())

      modelActor ! ModelCommand.Initialize(model1, testOptimizer)

      val probe = createTestProbe[Double]()
      val testPoint = Point2D(0.5, 0.5)

      modelActor ! ModelCommand.GetPrediction(testPoint, probe.ref)
      val prediction1 = probe.receiveMessage()

      modelActor ! ModelCommand.TrainingCompleted(model2)
      modelActor ! ModelCommand.GetPrediction(testPoint, probe.ref)
      val prediction2 = probe.receiveMessage()

      prediction1 shouldBe prediction2
    }
  }
}