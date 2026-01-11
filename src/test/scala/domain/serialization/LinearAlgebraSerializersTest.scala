package domain.serialization

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import domain.data.LinearAlgebra.*
import domain.serialization.LinearAlgebraSerializers.given

class LinearAlgebraSerializersTest extends AnyFunSuite with Matchers {

  test("Vector serialization and deserialization preserves content") {
    val originalVector = Vector(1.0, 2.5, -3.0)
    val serialized = summon[Serializer[Vector]].serialize(originalVector)
    
    summon[Serializer[Vector]].deserialize(serialized).get shouldBe originalVector
  }

  test("Empty Vector serialization and deserialization works correctly") {
    val emptyVector = Vector.zeros(0)
    val serialized = summon[Serializer[Vector]].serialize(emptyVector)
    
    summon[Serializer[Vector]].deserialize(serialized).get shouldBe emptyVector
  }

  test("Matrix serialization and deserialization preserves dimensions and content") {
    val originalMatrix = Matrix.fill(2, 3)(1.5)
    val serialized = summon[Serializer[Matrix]].serialize(originalMatrix)
    
    summon[Serializer[Matrix]].deserialize(serialized).get shouldBe originalMatrix
  }

  test("Matrix with different values is deserialized correctly") {
    val matrix = Vector(1.0, 2.0) outer Vector(3.0, 4.0)
    val serialized = summon[Serializer[Matrix]].serialize(matrix)
    
    summon[Serializer[Matrix]].deserialize(serialized).get shouldBe matrix
  }
}
