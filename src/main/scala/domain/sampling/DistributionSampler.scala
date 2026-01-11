package domain.sampling

import domain.data.Point2D
import domain.pattern.PointDistribution
import domain.sampling.PointSampler

final case class DistributionSampler(
  distribution: PointDistribution
) extends PointSampler:

  override def sample(): Point2D = distribution.sample()
