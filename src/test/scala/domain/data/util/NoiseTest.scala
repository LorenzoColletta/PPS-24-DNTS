package domain.data.util

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import domain.data.{Point2D, util}
import domain.data.sampling.Domain
import domain.data.util.{Distribution, NoiseWithDistribution, Space}

class NoiseTest extends AnyFunSuite with Matchers:


  test("NoiseWithDistribution should add dx and dy to the point") {
    given Space = Space(width = 100.0, height = 100.0)

    val domain = Domain(-1.0, 1.0)

    val fixedDx = 0.5
    val fixedDy = -0.25
    var callCount = 0

    val fakeDistribution = new Distribution:
      override def sample(d: Domain): Double =
        callCount += 1
        if callCount == 1 then fixedDx else fixedDy

    val noise = NoiseWithDistribution(fakeDistribution, domain)

    val p = Point2D(5.0, 5.0)
    val result = noise(p)

    result shouldBe Point2D(
      5.0 + fixedDx,
      5.0 + fixedDy
    )

    callCount shouldBe 2
  }

  test("NoiseWithDistribution should clamp the resulting point to space bounds") {
    given Space = Space(width = 20.0, height = 20.0)

    val domain = Domain(-10.0, 10.0)

    val fakeDistribution = new Distribution:
      override def sample(d: Domain): Double = 10.0

    val noise = util.NoiseWithDistribution(fakeDistribution, domain)

    val p = Point2D(9.0, 9.0)
    val result = noise(p)

    result shouldBe Point2D(10.0, 10.0)
  }

  test("NoiseWithDistribution should pass the correct domain to the distribution") {
    given Space = Space(width = 100.0, height = 100.0)

    val domain = Domain(-2.0, 2.0)
    var receivedDomains: List[Domain] = Nil

    val spyDistribution = new Distribution:
      override def sample(d: Domain): Double =
        receivedDomains = d :: receivedDomains
        0.0

    val noise = util.NoiseWithDistribution(spyDistribution, domain)

    noise(Point2D(3.0, 3.0))

    receivedDomains shouldBe List(domain, domain)
  }

