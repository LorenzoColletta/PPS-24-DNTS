package domain.training

import domain.data.util.Space
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import domain.network.{Activations, Feature, ModelBuilder}
import domain.data.{Label, LabeledPoint2D, Point2D}
import domain.training.Strategies.Losses.mse

class TrainingCoreTest extends AnyFunSuite with Matchers {

  given Space = Space(50.0, 50.0)

  private final val dummyModel = ModelBuilder.fromInputs(Feature.X)
    .addLayer(neurons = 1, activation = Activations.Sigmoid)
    .withSeed(1234L)
    .build()

  private final val dummyBatch = List(
    LabeledPoint2D(Point2D(0.0, 0.0), Label.Negative),
    LabeledPoint2D(Point2D(1.0, 1.0), Label.Positive)
  )
  
  
  test("computeBatchGradients should return a gradient with the same topology as the network") {
    val (avgGrad, _) = TrainingCore.computeBatchGradients(dummyModel, dummyBatch)

    avgGrad.layers.length shouldBe dummyModel.network.layers.length
  }

  test("computeBatchGradients should return a valid non-negative loss value") {
    val (_, avgLoss) = TrainingCore.computeBatchGradients(dummyModel, dummyBatch)

    avgLoss should be >= 0.0
  }

  test("computeBatchGradients should produce non-zero gradients for learning") {
    val (avgGrad, _) = TrainingCore.computeBatchGradients(dummyModel, dummyBatch)
    val totalGradMagnitude = avgGrad.layers.map(_.wGrad.map(math.abs).toFlatList.sum).sum

    totalGradMagnitude should be > 0.0
  }
}
