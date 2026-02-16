package domain.dataset

import domain.data.Point2D
import domain.pattern.CircleRingPattern
import domain.sampling.{DistributionSampler, Domain}

/**
 * A binary dataset composed of two concentric circular regions.
 *
 * Positive samples are generated inside the inner circle,
 * while negative samples are generated in an outer ring.
 *
 * @param center       the center of the concentric rings
 * @param innerRadius  the radius of the inner circle (positive class)
 * @param outerRadius  the inner radius of the outer ring (negative class)
 * @param thickness    the thickness of the outer ring
 * @param domain       the bounding domain of the space
 * @param seedPositive optional seed for the positive sampler
 * @param seedNegative optional seed for the negative sampler
 */
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

