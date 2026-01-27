package actors

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import domain.data.{LabeledPoint2D, Point2D}
import domain.network.{Activations, Feature, HyperParams, Model, ModelBuilder, Regularization}
import org.scalatest.wordspec.AnyWordSpecLike
import actors.model.ModelActor
import actors.model.ModelActor.ModelCommand
import actors.trainer.TrainerActor
import actors.trainer.TrainerActor.TrainingConfig
import actors.trainer.TrainerActor.TrainerCommand
import akka.actor.typed.ActorRef
import config.{AppConfig, ProductionConfig}
import domain.data.Label
import domain.training.Strategies.Optimizers.SGD
import domain.training.Strategies.Regularizers

class ModelActorTest extends ScalaTestWithActorTestKit with AnyWordSpecLike {


  given AppConfig = ProductionConfig
  given domain.training.LossFunction = domain.training.Strategies.Losses.mse

  private final val dummyReg = Regularizers.fromConfig(Regularization.None)
  private final val testOptimizer = new SGD(learningRate = 0.01, reg = dummyReg)
  private final val dummyFeatures = Feature.X


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

  def createModel(seed: Long): Model =
    ModelBuilder.fromInputs(Feature.X, Feature.Y)
      .addLayer(2, Activations.Relu)
      .withSeed(seed)
      .build()

  def setup(model: Model): (ActorRef[ModelCommand], ActorRef[TrainerCommand]) =
    val modelActor = spawn(ModelActor())
    val trainerActor = spawn(TrainerActor(modelActor.ref))

    trainerActor ! TrainerCommand.SetTrainConfig(dummyConfig)
    trainerActor ! TrainerCommand.Start(dummyData, Nil)

    modelActor ! ModelCommand.Initialize(model, testOptimizer, trainerActor)

    (modelActor, trainerActor)

  "A ModelActor" should {

    "return different predictions after a model update" in {
      val model1 = createModel(1L)
      val model2 = createModel(999L)

      val (modelActor, trainerActor) = setup(model1)

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

      val (modelActor, trainerActor) = setup(model1)

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