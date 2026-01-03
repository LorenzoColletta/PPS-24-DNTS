package domain.data

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import domain.data.LinearAlgebra.*

class LinearAlgebraTest extends AnyFunSuite with Matchers {

  test("Vector creation with varargs has correct length") {
    Vector(1.0, 2.0, 3.0).length shouldBe 3
  }

  test("Vector creation with zeros has correct content") {
    Vector.zeros(2).toList shouldBe List(0.0, 0.0)
  }

  test("Vector fromList creates correct vector") {
    Vector.fromList(List(1.0, 5.0))(1) shouldBe 5.0
  }

  test("Vector sum adds elements pairwise") {
    (Vector(1.0, 2.0) + Vector(3.0, 4.0)).toList shouldBe List(4.0, 6.0)
  }

  test("Vector subtraction subtracts elements pairwise") {
    (Vector(10.0, 20.0) - Vector(1.0, 2.0)).toList shouldBe List(9.0, 18.0)
  }

  test("Vector scalar multiplication scales all elements") {
    (Vector(2.0, 4.0) * 3.0).toList shouldBe List(6.0, 12.0)
  }

  test("Vector scalar division divides all elements") {
    (Vector(10.0, 20.0) / 2.0).toList shouldBe List(5.0, 10.0)
  }

  test("Vector dot product returns sum of products") {
    (Vector(1.0, 2.0) dot Vector(3.0, 4.0)) shouldBe 11.0
  }

  test("Vector element-wise multiplication multiplies pairwise") {
    (Vector(2.0, 3.0) |*| Vector(4.0, 5.0)).toList shouldBe List(8.0, 15.0)
  }

  test("Matrix creation with fill sets correct dimensions") {
    Matrix.fill(2, 3)(1.0).cols shouldBe 3
  }

  test("Matrix creation with zeros sets content to zero") {
    val m = Matrix.zeros(1, 1)
    (m * Vector(1.0)).toList shouldBe List(0.0)
  }

  test("Matrix addition sums elements pairwise") {
    val m1 = Matrix.fill(1, 1)(2.0)
    val m2 = Matrix.fill(1, 1)(3.0)
    ((m1 + m2) * Vector(1.0)).toList shouldBe List(5.0)
  }

  test("Matrix multiplication with scalar scales elements") {
    val m = Matrix.fill(2, 2)(2.0)
    val scaled = m * 5.0
    (scaled * Vector(1.0, 1.0)).toList shouldBe List(20.0, 20.0)
  }

  test("Matrix multiplication with Vector performs linear transformation") {
    val m = Matrix.fill(2, 2)(1.0)
    val v = Vector(2.0, 3.0)
    (m * v).toList shouldBe List(5.0, 5.0)
  }

  test("Matrix transpose swaps dimensions") {
    Matrix.zeros(2, 5).T.rows shouldBe 5
  }
}