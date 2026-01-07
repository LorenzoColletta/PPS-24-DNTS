package domain.serialization

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import domain.network.{Network, Layer, Activations}
import domain.network.Activation
import domain.data.LinearAlgebra.{Matrix, Vector}
import domain.network.Activations.given
import domain.serialization.NetworkSerializers.given
import domain.serialization.LinearAlgebraSerializers.given

class NetworkSerializersTest extends AnyFunSuite with Matchers {

  private def createTestLayer(rows: Int, cols: Int, value: Double, act: Activation): Layer =
    Layer(
      weights = Matrix.fill(rows, cols)(value),
      biases = Vector.fromList(List.fill(rows)(value)),
      activation = act
    )

  test("Network serialization preserves full structure and values") {
    val layer1 = createTestLayer(2, 2, 0.5, Activations.Sigmoid)
    val layer2 = createTestLayer(1, 2, 0.8, Activations.Sigmoid)
    val originalNetwork = Network(List(layer1, layer2))

    val serialized = summon[Serializer[Network]].serialize(originalNetwork)
    val deserialized = summon[Serializer[Network]].deserialize(serialized).get

    deserialized shouldBe originalNetwork
  }

  test("Network serialization handles empty network correctly") {
    val emptyNetwork = Network(List.empty)
    val serialized = summon[Serializer[Network]].serialize(emptyNetwork)
    val deserialized = summon[Serializer[Network]].deserialize(serialized).get

    deserialized shouldBe emptyNetwork
  }

  test("Network serialization preserves mixed activation functions") {
    val net = Network(List(
      createTestLayer(1, 1, 0.1, Activations.Sigmoid),
      createTestLayer(1, 1, 0.1, Activations.Relu)
    ))

    val serialized = summon[Serializer[Network]].serialize(net)
    val deserialized = summon[Serializer[Network]].deserialize(serialized).get

    deserialized shouldBe net
  }
}
