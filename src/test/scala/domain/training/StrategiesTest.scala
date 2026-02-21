package domain.training

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import domain.data.LinearAlgebra.*
import domain.network.{Network, Layer, Regularization}
import domain.network.Activations
import Strategies.*

class StrategiesTest extends AnyFunSuite with Matchers {

  test("MSE compute returns correct normalized squared error") {
    val loss = Strategies.Losses.mse.compute(Vector(2.0, 4.0), Vector(0.0, 0.0))

    loss shouldBe 10.0
  }

  test("MSE derivative returns correct normalized gradient vector") {
    val derivative = Strategies.Losses.mse.derivative(Vector(10.0, 20.0), Vector(4.0, 14.0))

    derivative.toList shouldBe List(6.0, 6.0)
  }


  test("L2 Regularization applies weight decay correctly") {
    val l2 = Regularizers.fromConfig(Regularization.L2(0.2))
    val w = Matrix.fill(1, 1)(10.0)

    val regW = l2(w, 0.5)

    (regW * Vector(1.0)).toList shouldBe List(9.0)
  }

  test("L1 Regularization applies Soft Thresholding") {
    val l1 = Regularizers.fromConfig(Regularization.L1(0.2))
    val w = Matrix.fill(1, 1)(0.1)

    val regW = l1(w, 1.0)

    (regW * Vector(1.0)).toList shouldBe List(0.0)
  }

  test("L1 Regularization applies shift for large weights") {
    val l1 = Regularizers.fromConfig(Regularization.L1(0.2))
    val w = Matrix.fill(1, 1)(1.0)

    val regW = l1(w, 1.0)

    (regW * Vector(1.0)).headOption.get shouldBe 0.8 +- 0.0001
  }


  test("SGD updates weights by subtracting scaled gradient") {
    val optimizer = new Optimizers.SGD(0.5, Regularizers.fromConfig(Regularization.None))

    val layer = Layer(Matrix.fill(1, 1)(10.0), Vector(0.0), Activations.Relu)
    val net = Network(List(layer))
    val grad = NetworkGradient(List(LayerGradient(Matrix.fill(1, 1)(2.0), Vector(0.0))))

    val newNet = optimizer.updateWeights(net, grad)

    (newNet.layers.head.weights * Vector(1.0)).toList shouldBe List(9.0)
  }
}
