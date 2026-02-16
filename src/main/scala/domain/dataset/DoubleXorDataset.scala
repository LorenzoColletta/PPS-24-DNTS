package domain.dataset

import domain.pattern.Quadrant
import domain.sampling.{Domain, XorSampler}

/**
 * A binary dataset arranged in a double XOR configuration.
 *
 * Positive samples are generated in the first and third quadrants,
 * while negative samples are generated in the second and fourth quadrants.
 *
 * @param domain       the sampling domain defining the bounds of the space
 * @param seedPositive optional seed for the positive sampler
 * @param seedNegative optional seed for the negative sampler
 */
final class DoubleXorDataset(
  domain: Domain,
  seedPositive: Option[Long] = None,
  seedNegative: Option[Long] = None
) extends BinaryDataset(
  positive = XorSampler(domain, (Quadrant.I, Quadrant.III), seedPositive),
  negative = XorSampler(domain, (Quadrant.II, Quadrant.IV), seedNegative)
)
