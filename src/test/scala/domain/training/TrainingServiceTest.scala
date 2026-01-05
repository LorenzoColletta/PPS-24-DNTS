package domain.training

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import domain.data.*
import domain.network.*
import domain.data.LinearAlgebra.*

class TrainingServiceTest extends AnyFunSuite with Matchers {

  test("Training on a single point forces loss to zero") {
    val singlePt = List(LabeledPoint2D(Point2D(0.5, 0.5), Label.Positive))
    val model = NetworkBuilder.fromInputs(Feature.X, Feature.Y).build()
    val hp = HyperParams(learningRate = 2.0, regularization = Regularization.None)

    val (_, history) = TrainingService.train(
      model.network,
      singlePt,
      List(Feature.X, Feature.Y),
      hp,
      epochs = 100,
      batchSize = 1
    )

    history.last should be < 0.001
  }

  test("Training solves linearly separable problem") {
    val andData = List(
      LabeledPoint2D(Point2D(0.0, 0.0), Label.Negative),
      LabeledPoint2D(Point2D(0.0, 1.0), Label.Negative),
      LabeledPoint2D(Point2D(1.0, 0.0), Label.Negative),
      LabeledPoint2D(Point2D(1.0, 1.0), Label.Positive)
    )

    val model = NetworkBuilder.fromInputs(Feature.X, Feature.Y).build()
    val hp = HyperParams(learningRate = 1.0)

    val (net, _) = TrainingService.train(
      model.network,
      andData,
      List(Feature.X, Feature.Y),
      hp,
      epochs = 1000,
      batchSize = 4
    )

    val output = net.forward(Vector(1.0, 1.0)).headOption.getOrElse(0.0)

    output should be > 0.8
  }

  test("Weights change after training epoch") {
    val data = List(LabeledPoint2D(Point2D(1.0, 1.0), Label.Positive))
    val model = NetworkBuilder.fromInputs(Feature.X).build()
    val startW = model.network.layers.head.weights

    val (net, _) = TrainingService.train(
      model.network,
      data,
      List(Feature.X),
      HyperParams(0.1),
      1,
      1
    )

    net.layers.head.weights should not be startW
  }
}
