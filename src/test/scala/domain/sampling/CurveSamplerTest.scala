package domain.sampling

import domain.data.Point2D
import domain.data.pattern.SpiralCurve
import domain.data.sampling.{CurveSampler, Domain, SpiralDomain}
import domain.data.util.{Distribution, Space, UniformDistribution}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class CurveSamplerTest extends AnyFunSuite with Matchers:

  given Space = Space(width = 100.0, height = 100.0)

  test("CurveSampler.sample should return curve.at(parameter)"):

    val curve = SpiralCurve(
      startDistance = 1.0,
      branchDistance = 2.0,
      rotation = 0.0
    )

    val fixedParameter = 1.5
    val sampler = CurveSampler(
      curve,
      () => fixedParameter
    )

    val point = sampler.sample()
    val expected = curve.at(fixedParameter)

    point shouldBe expected

  test("spiralSampler sampled values should spread away from origin"):

    val curve = SpiralCurve(
      startDistance = 1.0,
      branchDistance = 1.0,
      rotation = 0.0
    )

    val distribution = new UniformDistribution(seed = Some(42L))
    val sampler = CurveSampler.spiralSampler(curve, distribution)

    val samples = (1 to 100).map(_ => sampler.sample())

    samples.foreach { p =>
      val radius = math.sqrt(p.x * p.x + p.y * p.y)

      radius should be >= curve.startDistance
    }

  test("spiralSampler should be deterministic with same seed"):

    val curve = SpiralCurve(
      startDistance = 2.0,
      branchDistance = 0.5,
      rotation = 1.0
    )

    val dist1 = new UniformDistribution(seed = Some(123L))
    val dist2 = new UniformDistribution(seed = Some(123L))

    val sampler1 = CurveSampler.spiralSampler(curve, dist1)
    val sampler2 = CurveSampler.spiralSampler(curve, dist2)

    val points1 = (1 to 10).map(_ => sampler1.sample())
    val points2 = (1 to 10).map(_ => sampler2.sample())

    points1 shouldBe points2

  test("spiralSampler should pass SpiralDomain to the distribution"):

    val curve = SpiralCurve(
      startDistance = 1.0,
      branchDistance = 1.0,
      rotation = 0.0
    )

    val domain = SpiralDomain.domain(curve)

    var receivedDomain: Option[Domain] = None
    val spyDistribution: Distribution = new Distribution:
      override def sample(d: Domain): Double =
        receivedDomain = Some(d)
        0.0

    val sampler = CurveSampler.spiralSampler(curve, spyDistribution)

    sampler.sample()

    receivedDomain shouldBe Some(domain)

