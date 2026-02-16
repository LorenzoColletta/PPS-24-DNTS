package domain.pattern

import domain.data.Point2D
import domain.sampling.Domain
import domain.util.NormalDistribution

import scala.annotation.tailrec

/**
 * Implements a 2d point generation in a normal distribution.
 *
 * @param meanX the mean parameter along the x coordinate
 * @param meanY the mean parameter along the y coordinate
 * @param sigma the variance
 * @param domain the domain of valid values
 * @param radius the maximum radius of accepted values
 * @param seed the seed for random generation
 */
case class GaussianCluster(
  meanX: Double,
  meanY: Double,
  sigma: Double,
  domain: Domain,
  radius: Double,
  seed: Option[Long] = None
) extends PointDistribution:

  require(
    meanX >= domain.min - 3 * sigma &&
      meanX <= domain.max + 3 * sigma,
    "meanX too far from domain"
  )

  require(
    meanY >= domain.min - 3 * sigma &&
      meanY <= domain.max + 3 * sigma,
    "meanY too far from domain"
  )

  private val xDistribution = NormalDistribution(meanX, sigma, seed)
  private val yDistribution = NormalDistribution(meanY, sigma, seed)

  private def isInside(p: Point2D): Boolean =
    val dx = p.x - meanX
    val dy = p.y - meanY
    dx * dx + dy * dy <= radius * radius

  /**
   * Generates a 2d point using a normal distribution.
   *
   *  @return the generated point
   */
  @tailrec
  final override def sample(): Point2D =
    val p = Point2D(
      xDistribution.sample(domain),
      yDistribution.sample(domain)
    )

    if isInside(p) then p else sample()
