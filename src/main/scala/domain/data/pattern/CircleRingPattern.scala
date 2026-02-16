package domain.data.pattern

import domain.data.Point2D
import domain.data.sampling.Domain
import domain.data.util.UniformDistribution

import scala.annotation.tailrec
import scala.math.{cos, sin, sqrt, Pi}

final case class CircleRingPattern(
  center: Point2D,
  minRadius: Double,
  maxRadius: Double,
  domain: Domain,
  seed: Option[Long] = None
) extends PointDistribution:

  require(minRadius >= 0, "minRadius must be >= 0")
  require(maxRadius > minRadius, "maxRadius must be > minRadius")

  private val distribution = UniformDistribution(seed)

  @tailrec
  override def sample(): Point2D =
    val angle = distribution.sample(Domain(0, 2 * Pi))
    val radius = sqrt(
      distribution.sample(Domain(minRadius * minRadius, maxRadius * maxRadius))
    )

    val p = Point2D(
      center.x + radius * cos(angle),
      center.y + radius * sin(angle)
    )

    if p.x >= domain.min && p.x <= domain.max &&
      p.y >= domain.min && p.y <= domain.max
    then p
    else sample()
