package domain.serialization

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import domain.network.{Network, Layer, Activations}
import domain.data.LinearAlgebra.{Matrix, Vector}
import domain.network.Activations.given
import domain.serialization.NetworkSerializers.given
import domain.serialization.LinearAlgebraSerializers.given

class AkkaSerializerAdapterTest extends AnyFunSuite with Matchers {

  private final val adapter = new AkkaSerializerAdapter()

  private def createDummyNetwork(): Network =
    Network(List(
      Layer(Matrix.fill(1, 1)(0.5), Vector.fromList(List(0.1)), Activations.Sigmoid)
    ))

  test("Adapter identifier is correctly set") {
    adapter.identifier shouldBe 99999
  }

  test("Adapter properly serializes a Network to binary") {
    val net = createDummyNetwork()

    adapter.toBinary(net).length should be > 0
  }

  test("Adapter round-trip (serialize -> deserialize) preserves the Network") {
    val originalNet = createDummyNetwork()

    val bytes = adapter.toBinary(originalNet)
    val restoredObj = adapter.fromBinary(bytes, Some(classOf[Network]))

    restoredObj shouldBe originalNet
  }

  test("Adapter throws exception when serializing unsupported types") {
    an[IllegalArgumentException] should be thrownBy {
      adapter.toBinary("I am not a neural network")
    }
  }

  test("Adapter should throw exception when deserializing with unknown manifest") {
    val dummyBytes = Array[Byte](1, 2, 3)

    an [IllegalArgumentException] should be thrownBy {
      adapter.fromBinary(dummyBytes, Some(classOf[String]))
    }
  }

  test("Adapter should throw exception when deserializing without manifest") {
    val dummyBytes = Array[Byte](1, 2, 3)

    an [IllegalArgumentException] should be thrownBy {
      adapter.fromBinary(dummyBytes, None)
    }
  }
}