package domain.pattern

import domain.data.Point2D
import domain.sampling.Domain
import domain.util.UniformDistribution

import scala.annotation.tailrec
import scala.math.{cos, sin, sqrt, Pi}

/**
 * Implements a 2d point generation inside a circular area using a normal distribution.
 *
 * @param center the center of the area
 * @param minRadius the minimum distance from the center
 * @param maxRadius the maximum distance from the center
 * @param domain the of valid values
 * @param seed the seed used for random generation
 */
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

  /**
   * Generates a point in a circular area using a uniform distribution.
   *
   * @return the generated point
   */
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
