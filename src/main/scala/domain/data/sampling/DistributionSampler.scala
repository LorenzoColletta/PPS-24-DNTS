package domain.data.sampling

import domain.data.Point2D
import domain.data.pattern.PointDistribution

final case class DistributionSampler(
  distribution: PointDistribution
) extends PointSampler:

  override def sample(): Point2D = distribution.sample()
