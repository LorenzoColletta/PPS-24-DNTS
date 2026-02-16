package domain.data.dataset

import domain.data.Point2D
import domain.data.pattern.CircleRingPattern
import domain.data.sampling.{DistributionSampler, Domain}

final class DoubleRingDataset(
 center: Point2D,
 innerRadius: Double,
 outerRadius: Double,
 thickness: Double,
 domain: Domain,
 seedPositive: Option[Long] = None,
 seedNegative: Option[Long] = None
) extends BinaryDataset(
  positive = DistributionSampler(
    CircleRingPattern(
      center = center,
      minRadius = 0.0,
      maxRadius = innerRadius,
      domain = domain,
      seed = seedPositive
    )
  ),
  negative = DistributionSampler(
    CircleRingPattern(
      center = center,
      minRadius = outerRadius,
      maxRadius = outerRadius + thickness,
      domain = domain,
      seed = seedNegative
    )
  )
)

