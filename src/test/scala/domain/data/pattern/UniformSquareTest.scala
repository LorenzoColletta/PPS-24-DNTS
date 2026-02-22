package domain.data.pattern

import domain.data.Point2D
import domain.data.sampling.Domain
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class UniformSquareTest extends AnyFunSuite with Matchers:

  private val domain = Domain(-10.0, 10.0)

  private def assertQuadrant(p: Point2D, quadrant: Quadrant): Unit =
    quadrant match
      case Quadrant.I =>
        p.x should be >= 0.0
        p.y should be >= 0.0

      case Quadrant.II =>
        p.x should be <= 0.0
        p.y should be >= 0.0

      case Quadrant.III =>
        p.x should be <= 0.0
        p.y should be <= 0.0

      case Quadrant.IV =>
        p.x should be >= 0.0
        p.y should be <= 0.0

  test("generated points should always lie inside the domain bounds"):
    val pattern = UniformSquare(
      domain = domain,
      quadrant = Quadrant.I,
      seed = Some(42)
    )

    val points = List.fill(1000)(pattern.sample())

    all(points.map(_.x.abs)) should be <= domain.max
    all(points.map(_.y.abs)) should be <= domain.max


  test("quadrant I should generate points with x >= 0 and y >= 0"):
    val pattern = UniformSquare(domain, Quadrant.I, seed = Some(1))

    val points = List.fill(500)(pattern.sample())
    points.foreach(assertQuadrant(_, Quadrant.I))


  test("quadrant II should generate points with x <= 0 and y >= 0"):
    val pattern = UniformSquare(domain, Quadrant.II, seed = Some(2))

    val points = List.fill(500)(pattern.sample())
    points.foreach(assertQuadrant(_, Quadrant.II))


  test("quadrant III should generate points with x <= 0 and y <= 0"):
    val pattern = UniformSquare(domain, Quadrant.III, seed = Some(3))

    val points = List.fill(500)(pattern.sample())
    points.foreach(assertQuadrant(_, Quadrant.III))


  test("quadrant IV should generate points with x >= 0 and y <= 0"):
    val pattern = UniformSquare(domain, Quadrant.IV, seed = Some(4))

    val points = List.fill(500)(pattern.sample())
    points.foreach(assertQuadrant(_, Quadrant.IV))


  test("pattern should be deterministic when using the same seed"):
    val p1 = UniformSquare(domain, Quadrant.I, seed = Some(123))
    val p2 = UniformSquare(domain, Quadrant.I, seed = Some(123))

    val samples1 = List.fill(100)(p1.sample())
    val samples2 = List.fill(100)(p2.sample())

    samples1 shouldEqual samples2

