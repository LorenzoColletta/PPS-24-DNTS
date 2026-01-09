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

  test("Adapter exposes correct public constant for Network manifest") {
    AkkaSerializerAdapter.ManifestNetwork shouldBe "N"
  }

  test("Adapter produces correct manifest string for a Network object") {
    val net = createDummyNetwork()

    adapter.manifest(net) shouldBe AkkaSerializerAdapter.ManifestNetwork
  }

  test("Adapter properly serializes a Network to binary") {
    val net = createDummyNetwork()

    adapter.toBinary(net).length should be > 0
  }

  test("Adapter round-trip preserves the Network") {
    val originalNet = createDummyNetwork()

    val bytes = adapter.toBinary(originalNet)
    val restoredObj = adapter.fromBinary(bytes, AkkaSerializerAdapter.ManifestNetwork)

    restoredObj shouldBe originalNet
  }

  test("Adapter throws exception when asked for manifest of unsupported type") {
    an[IllegalArgumentException] should be thrownBy {
      adapter.manifest("I am just a String")
    }
  }

  test("Adapter throws exception when serializing unsupported types") {
    val unsupportedObject = List.fill(1, 2, 3, 4)

    an[IllegalArgumentException] should be thrownBy {
      adapter.toBinary(unsupportedObject)
    }
  }

  test("Adapter should throw exception when deserializing with unknown manifest") {
    val dummyBytes = Array[Byte](1, 2, 3)
    val unknownManifest = "UNKNOWN_TYPE"

    an [IllegalArgumentException] should be thrownBy {
      adapter.fromBinary(dummyBytes, unknownManifest)
    }
  }

  test("Adapter should throw exception when deserializing without manifest") {
    val dummyBytes = Array[Byte](1, 2, 3)

    an [IllegalArgumentException] should be thrownBy {
      adapter.fromBinary(dummyBytes, None)
    }
  }

  test("Adapter throws exception when deserialization fails due to corrupted bytes") {
    val corruptedBytes = Array[Byte](0, 0, 0, 1)

    an[IllegalArgumentException] should be thrownBy {
      adapter.fromBinary(corruptedBytes, AkkaSerializerAdapter.ManifestNetwork)
    }
  }
}