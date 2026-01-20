package actors

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import domain.network.{Activations, Feature, Model, ModelBuilder, Regularization}
import domain.training.Strategies
import org.scalatest.wordspec.AnyWordSpecLike
import actors.ModelActor.ModelCommand
import domain.training.Consensus.averageWith

class ModelActorTest extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  private val noReg = Strategies.Regularizers.fromConfig(Regularization.None)
  private val testOptimizer = new Strategies.Optimizers.SGD(learningRate = 0.01, reg = noReg)

  def createModel(seed: Long): Model =
    ModelBuilder.fromInputs(Feature.X, Feature.Y)
      .addLayer(2, Activations.Relu)
      .withSeed(seed)
      .build()

  "A ModelActor" should {

    "return the current model when requested" in {
      val model1 = createModel(1L)
      val modelActor = spawn(ModelActor(model1, testOptimizer))
      val probe = createTestProbe[Model]()

      modelActor ! ModelCommand.GetModel(probe.ref)

      val receivedModel = probe.receiveMessage()
      receivedModel shouldBe model1
    }

    "update the model when TrainingCompleted is received" in {
      val model1 = createModel(1L)
      val model2 = createModel(999L)
      val modelActor = spawn(ModelActor(model1, testOptimizer))
      val probe = createTestProbe[Model]()

      modelActor ! ModelCommand.TrainingCompleted(model2)
      modelActor ! ModelCommand.GetModel(probe.ref)

      probe.receiveMessage() shouldBe model2
    }

    "correctly merge models when SyncModel is received" in {
      val localModel = createModel(1L)
      val remoteModel = createModel(2L)
      val modelActor = spawn(ModelActor(localModel, testOptimizer))
      val probe = createTestProbe[Model]()

      modelActor ! ModelCommand.SyncModel(remoteModel)
      modelActor ! ModelCommand.GetModel(probe.ref)

      val mergedModel = probe.receiveMessage()
      mergedModel.network shouldNot be(localModel.network)
      mergedModel.network shouldNot be(remoteModel.network)

      val expectedNetwork = localModel.network averageWith remoteModel.network
      mergedModel.network shouldBe expectedNetwork
    }

    /*
    "provide metrics for the monitor" in {
      val model1 = createModel(1L)
      val modelActor = spawn(ModelActor(model1, testOptimizer))
      val probe = createTestProbe[actors.monitor.MonitorProtocol.MonitorCommand.ViewUpdateResponse]()

      modelActor ! ModelCommand.GetMetrics(probe.ref)

      val response = probe.receiveMessage()
      response.model shouldBe model1
    }*/
  }
}