package domain.pattern

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class SpiralCurveTest extends AnyFunSuite with Matchers:

  val epsilon = 1e-6

  def approxEqual(a: Double, b: Double): Boolean =
    math.abs(a - b) <= epsilon

  test("at(0) should return point on x axis when rotation = 0"):
    val curve = SpiralCurve(
      startDistance = 10.0,
      branchDistance = 2.0,
      rotation = 0.0
    )

    val p = curve.at(0.0)

    approxEqual(p.x, 10.0) shouldBe true
    approxEqual(p.y, 0.0) shouldBe true


  test("radius should increase linearly with x"):
    val curve = SpiralCurve(
      startDistance = 5.0,
      branchDistance = 3.0,
      rotation = 0.0
    )

    val p1 = curve.at(1.0)
    val p2 = curve.at(2.0)

    val r1 = math.hypot(p1.x, p1.y)
    val r2 = math.hypot(p2.x, p2.y)

    approxEqual(r1, 8.0) shouldBe true
    approxEqual(r2, 11.0) shouldBe true


  test("rotation should rotate the curve"):
    val curve = SpiralCurve(
      startDistance = 10.0,
      branchDistance = 0.0,
      rotation = math.Pi / 2
    )

    val p = curve.at(0.0)

    approxEqual(p.x, 0.0) shouldBe true
    approxEqual(p.y, 10.0) shouldBe true


  test("negative x should use absolute value for angle"):
    val curve = SpiralCurve(
      startDistance = 10.0,
      branchDistance = 0.0,
      rotation = 0.0
    )

    val pPos = curve.at(1.0)
    val pNeg = curve.at(-1.0)

    approxEqual(pPos.x, pNeg.x) shouldBe true
    approxEqual(pPos.y, pNeg.y) shouldBe true


  test("at(x) should return a point at the expected polar angle"):
    val curve = SpiralCurve(
      startDistance = 0.0,
      branchDistance = 1.0,
      rotation = 0.0
    )

    val x = math.Pi
    val p = curve.at(x)

    approxEqual(p.x, x * math.cos(x)) shouldBe true
    approxEqual(p.y, x * math.sin(x)) shouldBe true


