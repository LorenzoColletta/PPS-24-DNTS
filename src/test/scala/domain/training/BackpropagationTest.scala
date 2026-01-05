package domain.training

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import domain.data.*
import domain.network.*
import domain.network.Activations
import domain.data.LinearAlgebra.*
import Strategies.Losses.given

class BackpropagationTest extends AnyFunSuite with Matchers {

  private final val identity = new Activation {
    def name = "Id";
    def apply(x: Double): Double = x;
    def derivative(x: Double) = 1.0
    def standardDeviation(in: Int, out: Int) = 1.0
  }

  private final val layer1 = Layer(Matrix.fill(1, 1)(1.0), Vector(0.0), identity)
  private final val layer2 = Layer(Matrix.fill(1, 1)(1.0), Vector(0.0), identity)
  private final val simpleNet = Network(List(layer1, layer2))

  private final val inputPoint = LabeledPoint2D(Point2D(2.0, 0.0), Label.Positive)
  private final val features = List(Feature.X)


  test("Backprop produces same number of gradients as layers") {
    val grads = Backpropagation.computeGradients(simpleNet, inputPoint, features)

    grads.length shouldBe 2
  }

  test("Gradient dimensions match layer weight dimensions") {
    val grads = Backpropagation.computeGradients(simpleNet, inputPoint, features)
    val g1 = grads.head

    (g1.wGrad.rows, g1.wGrad.cols) shouldBe (1, 1)
  }

  test("Correct gradient calculation for Output Layer") {
    val grads = Backpropagation.computeGradients(simpleNet, inputPoint, features)
    val lastLayerGrad = grads.last

    (lastLayerGrad.wGrad * Vector(1.0)).headOption.get shouldBe 2.0
  }

  test("Correct error propagation to Hidden Layer") {
    val grads = Backpropagation.computeGradients(simpleNet, inputPoint, features)
    val firstLayerGrad = grads.head

    (firstLayerGrad.wGrad * Vector(1.0)).headOption.get shouldBe 2.0
  }
}
