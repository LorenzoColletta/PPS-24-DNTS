package domain.data.util

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import domain.data.Point2D

class SpaceTest extends AnyFunSuite with Matchers {

  test("Space should not allow non-positive width") {
    an[IllegalArgumentException] should be thrownBy {
      Space(0.0, 10.0)
    }
  }

  test("Space should not allow non-positive height") {
    an[IllegalArgumentException] should be thrownBy {
      Space(10.0, 0.0)
    }
  }

  test("clamp should return the same point if it is inside the centered space") {
    val space = Space(width = 10.0, height = 6.0)
    val p = Point2D(2.0, -1.0)

    space.clamp(p) shouldBe p
  }

  test("clamp should clamp values below the minimum bounds") {
    val space = Space(width = 10.0, height = 6.0)
    val p = Point2D(-10.0, -5.0)

    space.clamp(p) shouldBe Point2D(-5.0, -3.0)
  }

  test("clamp should clamp values above the maximum bounds") {
    val space = Space(width = 10.0, height = 6.0)
    val p = Point2D(10.0, 5.0)

    space.clamp(p) shouldBe Point2D(5.0, 3.0)
  }

  test("clamp should clamp mixed out-of-bound coordinates") {
    val space = Space(width = 10.0, height = 6.0)
    val p = Point2D(-7.0, 4.0)

    space.clamp(p) shouldBe Point2D(-5.0, 3.0)
  }
}
