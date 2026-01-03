package domain.network

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import domain.data.LinearAlgebra.*
import domain.data.Point2D

class NetworkTest extends AnyFunSuite with Matchers {

  val identityActivation: Activation = new Activation {
    def name = "Identity"
    def apply(x: Double): Double = x
    def derivative(x: Double): Double = 1.0
    def standardDeviation(nIn: Int, nOut: Int): Double = 1.0
  }

  val doubleActivation: Activation = new Activation {
    def name = "Double"
    def apply(x: Double): Double = x * 2.0
    def derivative(x: Double): Double = 2.0
    def standardDeviation(nIn: Int, nOut: Int): Double = 1.0
  }

  test("Network forward propagates through multiple layers") {
    val l1 = Layer(Matrix.fill(2, 2)(1.0), Vector(0.0, 0.0), identityActivation)
    val l2 = Layer(Matrix.fill(2, 2)(0.5), Vector(0.0, 0.0), identityActivation)
    val net = Network(List(l1, l2))

    net.forward(Vector(1.0, 1.0)).toList shouldBe List(2.0, 2.0)
  }

  test("Model prediction selects first element of network output") {
    val l1 = Layer(Matrix.fill(1, 1)(0.0), Vector(5.0), identityActivation)
    val net = Network(List(l1))

    val model = Model(net, List(Feature.X))

    model.predict(Point2D(10.0, 20.0)) shouldBe 5.0
  }

  test("Complex prediction with features transformation, weighted sum and activation") {
    val point = Point2D(2.0, 1.0)
    val features = List(Feature.X, Feature.Y)

    val weightIter = Iterator.from(1).map(_.toDouble)
    val weights = Matrix.fill(1, 2)(weightIter.next())
    val bias = Vector(1.0)

    val layer = Layer(weights, bias, doubleActivation)
    val model = Model(Network(List(layer)), features)

    model.predict(point) shouldBe 10.0
  }
}
