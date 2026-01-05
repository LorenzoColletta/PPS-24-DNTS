package domain.network

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import domain.data.Point2D
import domain.network.Activations.given

class NetworkBuilderTest extends AnyFunSuite with Matchers {

  test("Builder correctly stores input features") {
    NetworkBuilder.fromInputs(Feature.X, Feature.Y).features shouldBe List(Feature.X, Feature.Y)
  }

  test("Builder accumulates layer configurations with correct activations") {
    NetworkBuilder.fromInputs(Feature.X)
      .addLayer(10, Activations.relu)
      .hiddenLayers.head.activation.name shouldBe "ReLU"
  }

  test("Build generates a Model with input dimension matching feature count") {
    val model = NetworkBuilder.fromInputs(Feature.X, Feature.Y, Feature.ProductXY)
      .addLayer(5, Activations.tanh)
      .build()

    model.network.layers.head.weights.cols shouldBe 3
  }

  test("Build forces Sigmoid activation on the output layer") {
    val model = NetworkBuilder.fromInputs(Feature.X)
      .addLayer(5, Activations.relu)
      .build()

    model.network.layers.last.activation.name shouldBe "Sigmoid"
  }

  test("Model incorporates Feature transformation in prediction pipeline") {
    val model = NetworkBuilder.fromInputs(Feature.X, Feature.Y)
      .addLayer(2, Activations.sigmoid)
      .build()

    model.predict(Point2D(1.0, 1.0)) should (be >= 0.0 and be <= 1.0)
  }

  test("Built network propagates forward pass without runtime errors") {
    val model = NetworkBuilder.fromInputs(Feature.X)
      .addLayer(10, Activations.relu)
      .addLayer(10, Activations.tanh)
      .build()

    noException should be thrownBy model.predict(Point2D(0.5, 0.5))
  }

  test("Builder with same seed should produce identical weights") {
    val seed = 12345L

    val net1 = NetworkBuilder.fromInputs(Feature.X)
      .addLayer(2, Activations.sigmoid)
      .withSeed(seed)
      .build()
      .network

    val net2 = NetworkBuilder.fromInputs(Feature.X)
      .addLayer(2, Activations.sigmoid)
      .withSeed(seed)
      .build()
      .network

    (
      net1.layers.head.weights,
      net1.layers.head.biases
    ) shouldBe (
      net2.layers.head.weights,
      net2.layers.head.biases
    )
  }
}
