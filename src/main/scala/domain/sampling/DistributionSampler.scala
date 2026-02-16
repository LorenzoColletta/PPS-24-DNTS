package domain.sampling

import domain.data.Point2D
import domain.pattern.PointDistribution
import domain.sampling.PointSampler

/**
 * Implements [[PointSampler]], it generates 2d points using a given distribution.
 *
 * @param distribution the distribution used to generate the points
 */
final case class DistributionSampler(
  distribution: PointDistribution
) extends PointSampler:

  /**
   * Generates a single 2D point.
   * @return a 2d point
   */
  override def sample(): Point2D = distribution.sample()
