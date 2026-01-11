package domain.sampling

import domain.data.Point2D
import domain.pattern.Quadrant
import domain.sampling.Domain

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class XorSamplerTest extends AnyFunSuite with Matchers:

  private val domain = Domain(-10.0, 10.0)

  private def quadrantOf(p: Point2D): Quadrant =
    (p.x >= 0, p.y >= 0) match
      case (true,  true)  => Quadrant.I
      case (false, true)  => Quadrant.II
      case (false, false) => Quadrant.III
      case (true,  false) => Quadrant.IV

  test("generated points should belong to one of the configured quadrants") :
    val sampler = XorSampler(
      domain = domain,
      quadrants = (Quadrant.I, Quadrant.III),
      seed = Some(42)
    )

    val points = List.fill(1000)(sampler.sample())
    val quadrantsUsed = points.map(quadrantOf).toSet

    quadrantsUsed shouldBe Set(Quadrant.I, Quadrant.III)


  test("generated points should never belong to other quadrants") :
    val sampler = XorSampler(
      domain = domain,
      quadrants = (Quadrant.II, Quadrant.IV),
      seed = Some(123)
    )

    val points = List.fill(1000)(sampler.sample())

    points.foreach { p =>
      quadrantOf(p) should (be (Quadrant.II) or be (Quadrant.IV))
    }


  test("sampler should be deterministic when using the same seed"):
    val sampler1 = XorSampler(domain, (Quadrant.I, Quadrant.III), seed = Some(99))
    val sampler2 = XorSampler(domain, (Quadrant.I, Quadrant.III), seed = Some(99))

    val samples1 = List.fill(200)(sampler1.sample())
    val samples2 = List.fill(200)(sampler2.sample())

    samples1 shouldEqual samples2


  test("sampler should use both quadrants over multiple samples"):
    val sampler = XorSampler(
      domain = domain,
      quadrants = (Quadrant.I, Quadrant.IV),
      seed = Some(1)
    )

    val points = List.fill(1000)(sampler.sample())
    val quadrantsUsed = points.map(quadrantOf).toSet

    quadrantsUsed.size shouldBe 2
    quadrantsUsed should contain allOf (Quadrant.I, Quadrant.IV)

