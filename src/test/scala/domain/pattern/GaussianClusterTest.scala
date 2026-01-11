package domain.pattern

import domain.data.Point2D
import domain.sampling.Domain
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class GaussianClusterTest extends AnyFunSuite with Matchers:

  private def distanceSquared(p: Point2D, cx: Double, cy: Double): Double =
    val dx = p.x - cx
    val dy = p.y - cy
    dx * dx + dy * dy

  test("sample() should always return a point inside the radius"):
    val cluster = GaussianCluster(
      meanX = 0.0,
      meanY = 0.0,
      sigma = 1.0,
      Domain(-2, 2),
      2.0
    )

    for (_ <- 1 to 1000)
      val p = cluster.sample()
      distanceSquared(p, 0.0, 0.0) should be <= 4.0


  test("sample() should terminate for reasonable parameters"):
    val cluster: GaussianCluster = GaussianCluster(
      meanX = 10.0,
      meanY = -5.0,
      sigma = 0.5,
      Domain(-20, 20),
      3.0
    )

    noException shouldBe thrownBy {
      cluster.sample()
    }


  test("sampled points should be roughly centered around the mean"):
    val cluster = GaussianCluster(
      meanX = 5.0,
      meanY = 5.0,
      sigma = 1.0,
      Domain(-10, 10),
      10.0
    )

    val samples = (1 to 2000).map(_ => cluster.sample())

    val avgX = samples.map(_.x).sum / samples.size
    val avgY = samples.map(_.y).sum / samples.size

    avgX shouldBe (5.0 +- 0.2)
    avgY shouldBe (5.0 +- 0.2)





