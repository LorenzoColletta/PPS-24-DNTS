package domain.training

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import domain.network.{Network, Layer, Activations, Feature}
import domain.data.LinearAlgebra.{Matrix, Vector}
import domain.data.{LabeledPoint2D, Label, Point2D}
import domain.training.Strategies.Losses.mse

class TrainingCoreTest extends AnyFunSuite with Matchers {

  private final val dummyLayer = Layer(
    Matrix.fill(1, 1)(0.5),
    Vector.fromList(List(0.1)),
    Activations.Sigmoid
  )
  private final val dummyNetwork = Network(List(dummyLayer))

  private final val dummyBatch = List(
    LabeledPoint2D(Point2D(0.0, 0.0), Label.Negative),
    LabeledPoint2D(Point2D(1.0, 1.0), Label.Positive)
  )

  private final val dummyFeatures = List(Feature.X)

  test("computeBatchGradients should return a gradient with the same topology as the network") {
    val (avgGrad, avgLoss) = TrainingCore.computeBatchGradients(dummyNetwork, dummyBatch, dummyFeatures)

    avgGrad.layers.length shouldBe dummyNetwork.layers.length
  }

  test("computeBatchGradients should return a valid non-negative loss value") {
    val (_, avgLoss) = TrainingCore.computeBatchGradients(dummyNetwork, dummyBatch, dummyFeatures)

    avgLoss should be >= 0.0
  }

  test("computeBatchGradients should produce non-zero gradients for learning") {
    val (avgGrad, _) = TrainingCore.computeBatchGradients(dummyNetwork, dummyBatch, dummyFeatures)
    val totalGradMagnitude = avgGrad.layers.map(_.wGrad.map(math.abs).toFlatList.sum).sum

    totalGradMagnitude should be > 0.0
  }
}
