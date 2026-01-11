package domain.sampling

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import domain.data.Point2D
import domain.pattern.PointDistribution

class DistributionSamplerTest extends AnyFunSuite with Matchers {

  test("DistributionSampler should delegate sample to the underlying distribution") {

    val expectedPoint = Point2D(1.0, 2.0)
    var callCount = 0

    val fakeDistribution = new PointDistribution:
      override def sample(): Point2D =
        callCount += 1
        expectedPoint

    val sampler = DistributionSampler(fakeDistribution)

    val result = sampler.sample()

    result shouldBe expectedPoint
    callCount shouldBe 1
  }
}
