package domain.pattern

import domain.data.Point2D
import domain.sampling.Domain

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class CircleRingPatternTest extends AnyFunSuite with Matchers:

  private val domain = Domain(-10.0, 10.0)
  private val center = Point2D(0.0, 0.0)

  private def distance(p: Point2D): Double =
    math.sqrt(p.x * p.x + p.y * p.y)

  test("generated points should always lie inside the domain"):
    val pattern = CircleRingPattern(
      center = center,
      minRadius = 0.0,
      maxRadius = 5.0,
      domain = domain,
      seed = Some(42)
    )

    val points = List.fill(1000)(pattern.sample())

    all(points.map(_.x)) should (be >= domain.min and be <= domain.max)
    all(points.map(_.y)) should (be >= domain.min and be <= domain.max)


  test("generated points should lie within the specified radius range"):
    val minR = 2.0
    val maxR = 5.0

    val pattern = CircleRingPattern(
      center = center,
      minRadius = minR,
      maxRadius = maxR,
      domain = domain,
      seed = Some(123)
    )

    val points = List.fill(1000)(pattern.sample())

    all(points.map(distance)) should (be >= minR and be <= maxR)


  test("circle case (minRadius = 0) should generate points from center outward"):
    val pattern = CircleRingPattern(
      center = center,
      minRadius = 0.0,
      maxRadius = 4.0,
      domain = domain,
      seed = Some(7)
    )

    val points = List.fill(1000)(pattern.sample())

    all(points.map(distance)) should be <= 4.0


  test("ring case should not generate points inside minRadius"):
    val minR = 3.0
    val maxR = 6.0

    val pattern = CircleRingPattern(
      center = center,
      minRadius = minR,
      maxRadius = maxR,
      domain = domain,
      seed = Some(99)
    )

    val points = List.fill(1000)(pattern.sample())

    all(points.map(distance)) should be >= minR


  test("pattern should be deterministic when using the same seed"):
    val pattern1 = CircleRingPattern(
      center = center,
      minRadius = 1.0,
      maxRadius = 4.0,
      domain = domain,
      seed = Some(1234)
    )

    val pattern2 = CircleRingPattern(
      center = center,
      minRadius = 1.0,
      maxRadius = 4.0,
      domain = domain,
      seed = Some(1234)
    )

    val samples1 = List.fill(100)(pattern1.sample())
    val samples2 = List.fill(100)(pattern2.sample())

    samples1 shouldEqual samples2

