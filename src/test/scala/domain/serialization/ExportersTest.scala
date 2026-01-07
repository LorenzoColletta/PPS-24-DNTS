package domain.serialization

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import domain.network.{Network, Layer, Activations}
import domain.data.LinearAlgebra.{Matrix, Vector}
import domain.serialization.Exporters.given

class ExportersTest extends AnyFunSuite with Matchers {

  private final val dummyNet = Network(List(
    Layer(Matrix.zeros(1, 1), Vector.zeros(1), Activations.Sigmoid)
  ))
  private final val magicNumber = 123.456

  test("Network JSON export contains 'layer_index' field") {
    val json = summon[Exporter[Network]].jsonExport(dummyNet)
    json should include ("\"layer_index\"")
  }

  test("Network JSON export contains 'weights' field") {
    val json = summon[Exporter[Network]].jsonExport(dummyNet)
    json should include ("\"weights\"")
  }

  test("Network JSON export contains 'biases' field") {
    val json = summon[Exporter[Network]].jsonExport(dummyNet)
    json should include ("\"biases\"")
  }

  test("Network JSON export contains 'activation' field") {
    val json = summon[Exporter[Network]].jsonExport(dummyNet)
    json should include ("\"activation\"")
  }

  test("Network JSON export correctly formats numerical values") {
    val layer = Layer(
      weights = Matrix.fill(1, 1)(magicNumber),
      biases = Vector.zeros(1),
      activation = Activations.Sigmoid
    )
    val net = Network(List(layer))

    val json = summon[Exporter[Network]].jsonExport(net)

    json should include (magicNumber.toString)
  }

  test("Network JSON export includes correct activation name") {
    val layer = Layer(Matrix.zeros(1,1), Vector.zeros(1), Activations.Relu)
    val net = Network(List(layer))

    val json = summon[Exporter[Network]].jsonExport(net)

    json.toLowerCase should include (Activations.Relu.name.toLowerCase)
  }
}
