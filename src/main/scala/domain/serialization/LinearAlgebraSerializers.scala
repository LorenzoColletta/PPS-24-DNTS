package domain.serialization

import java.nio.ByteBuffer
import scala.util.Try

import domain.data.LinearAlgebra
import domain.data.LinearAlgebra.{Matrix, Vector}

object LinearAlgebraSerializers:

  given vectorSerializer: Serializer[Vector] with
    def serialize(v: Vector): Array[Byte] =
      val data = v.toList
      val capacity = 4 + (data.length * 8)
      val buffer = ByteBuffer.allocate(capacity)
      buffer.putInt(data.length)
      data.foreach(buffer.putDouble)
      buffer.array()

    def deserialize(bytes: Array[Byte]): Try[Vector] = Try {
      val buffer = ByteBuffer.wrap(bytes)
      val size = buffer.getInt
      val data = (0 until size).map(_ => buffer.getDouble).toList
      LinearAlgebra.Vector.fromList(data)
    }

  given matrixSerializer: Serializer[Matrix] with
    def serialize(m: Matrix): Array[Byte] =
      val rows = m.rows
      val cols = m.cols
      val data = m.toFlatList

      val capacity = 4 + 4 + (data.length * 8)
      val buffer = ByteBuffer.allocate(capacity)

      buffer.putInt(rows)
      buffer.putInt(cols)
      data.foreach(buffer.putDouble)
      buffer.array()

    def deserialize(bytes: Array[Byte]): Try[Matrix] = Try {
      val buffer = ByteBuffer.wrap(bytes)
      val rows = buffer.getInt
      val cols = buffer.getInt
      val totalDoubles = rows * cols

      val data = (0 until totalDoubles).map(_ => buffer.getDouble).toList
      LinearAlgebra.Matrix.fromData(rows, cols, data)
    }
