package domain.data

import scala.collection.immutable.Vector as ScalaVector

object LinearAlgebra:

  opaque type Vector = ScalaVector[Double]
  opaque type Matrix = ScalaVector[ScalaVector[Double]]

  object Vector:
    def apply(elems: Double*): Vector = ScalaVector(elems*)
    def zeros(size: Int): Vector = ScalaVector.fill(size)(0.0)
    def fromList(list: List[Double]): Vector = list.toVector

  object Matrix:
    def fill(rows: Int, cols: Int)(gen: => Double): Matrix =
      ScalaVector.fill(rows)(ScalaVector.fill(cols)(gen))
    def zeros(rows: Int, cols: Int): Matrix =
      ScalaVector.fill(rows)(ScalaVector.fill(cols)(0.0))

  extension (v: Vector)
    def length: Int = v.size
    def +(other: Vector): Vector = v.zip(other).map(_ + _)
    def -(other: Vector): Vector = v.zip(other).map(_ - _)
    def *(scalar: Double): Vector = v.map(_ * scalar)
    def /(scalar: Double): Vector = v.map(_ / scalar)
    def dot(other: Vector): Double = v.zip(other).map(_ * _).sum
    def |*|(other: Vector): Vector = v.zip(other).map(_ * _)
    def map(f: Double => Double): Vector = v.map(f)
    def toList: List[Double] = v.toList
    def headOption: Option[Double] = v.headOption
    def apply(i: Int): Double = v(i)

  extension (m: Matrix)
    def *(v: Vector): Vector = m.map(row => row dot v)
    def +(other: Matrix): Matrix =
      m.lazyZip(other).map((row1, row2) =>
        row1.lazyZip(row2).map(_ + _)
      )
    def *(scalar: Double): Matrix = m.map(_.map(_ * scalar))
    def T: Matrix =
      if m.isEmpty then ScalaVector.empty
      else m.transpose
    def map(f: Double => Double): Matrix = m.map(_.map(f))
    def rows: Int = m.size
    def cols: Int = if m.isEmpty then 0 else m.head.size
