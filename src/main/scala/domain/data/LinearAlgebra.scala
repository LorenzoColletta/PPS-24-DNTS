package domain.data

import scala.annotation.targetName
import scala.collection.immutable.Vector as ScalaVector

/**
 * Functional Linear Algebra primitives.
 * Implements immutable Vectors and Matrices using Opaque Types.
 */
object LinearAlgebra:

  /**
   * Represents an immutable mathematical vector of Doubles.
   */
  opaque type Vector = ScalaVector[Double]

  /**
   * Represents an immutable mathematical matrix of Doubles.
   */
  opaque type Matrix = ScalaVector[ScalaVector[Double]]


  /** Factory methods for constructing Vectors. */
  object Vector:
    /** Creates a Vector from a variable number of arguments. */
    def apply(elems: Double*): Vector = ScalaVector(elems*)

    /** Creates a Vector of a specific size filled with zeros. */
    def zeros(size: Int): Vector = ScalaVector.fill(size)(0.0)

    /** Converts a standard Scala List into a Vector. */
    def fromList(list: List[Double]): Vector = list.toVector

  /** Factory methods for constructing Matrices. */
  object Matrix:
    /**
     * Generates a Matrix by computing each element.
     *
     * @param gen A call-by-name parameter to generate values.
     */
    def fill(rows: Int, cols: Int)(gen: => Double): Matrix =
      ScalaVector.fill(rows)(ScalaVector.fill(cols)(gen))

    /** Creates a Matrix of specific dimensions filled with zeros. */
    def zeros(rows: Int, cols: Int): Matrix =
      ScalaVector.fill(rows)(ScalaVector.fill(cols)(0.0))

    /**
     * Constructs a Matrix from a flattened list of data.
     * The list is sliced into chunks of size `cols` to form rows.
     */
    def fromData(rows: Int, cols: Int, data: List[Double]): Matrix =
      val chunks = data.grouped(cols).toVector
      chunks.map(row => ScalaVector(row *))



  extension (v: Vector)
    def length: Int =
      v.size

    @targetName("vectorPlus")
    def +(other: Vector): Vector =
      v.zip(other).map(_ + _)

    @targetName("vectorMinus")
    def -(other: Vector): Vector =
      v.zip(other).map(_ - _)

    @targetName("vectorTimesScalar")
    def *(scalar: Double): Vector =
      v.map(_ * scalar)

    def /(scalar: Double): Vector =
      v.map(_ / scalar)

    infix def dot(other: Vector): Double =
      v.zip(other).map(_ * _).sum

    def |*|(other: Vector): Vector =
      v.zip(other).map(_ * _)

    infix def outer(other: Vector): Matrix =
      v.map(vi => other.map(oj => vi * oj))

    @targetName("vectorMap")
    def map(f: Double => Double): Vector = v.map(f)

    def toList: List[Double] = v.toList

    def headOption: Option[Double] = v.headOption

    @targetName("vectorApply")
    def apply(i: Int): Double = v(i)

  extension (m: Matrix)
    @targetName("matrixDivideScalar")
    def /(scalar: Double): Matrix =
      m.map(row => row / scalar)

    @targetName("matrixTimesVector")
    def *(v: Vector): Vector =
      require(m.head.length == v.length)
      m.map(row => row.dot(v))

    @targetName("matrixPlus")
    def +(other: Matrix): Matrix =
      m.lazyZip(other).map((row1, row2) =>
        row1.lazyZip(row2).map(_ + _)
      )

    @targetName("matrixMinus")
    def -(other: Matrix): Matrix =
      m.lazyZip(other).map((row1, row2) =>
        row1.lazyZip(row2).map(_ - _)
      )

    @targetName("matrixTimesScalar")
    def *(scalar: Double): Matrix = m.map(_.map(_ * scalar))

    def T: Matrix =
      if m.isEmpty then ScalaVector.empty
      else m.transpose

    @targetName("matrixMap")
    def map(f: Double => Double): Matrix = m.map(_.map(f))

    def rows: Int = m.size

    def cols: Int = if m.isEmpty then 0 else m.head.size

    def toFlatList: List[Double] = m.flatMap(_.toList).toList

    @targetName("matrixApply")
    def apply(i: Int): Vector = m(i)
