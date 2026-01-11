package domain.dataset

import domain.pattern.Quadrant
import domain.sampling.{Domain, XorSampler}

final class DoubleXorDataset(
  domain: Domain,
  seedPositive: Option[Long] = None,
  seedNegative: Option[Long] = None
) extends BinaryDataset(
  positive = XorSampler(domain, (Quadrant.I, Quadrant.III), seedPositive),
  negative = XorSampler(domain, (Quadrant.II, Quadrant.IV), seedNegative)
)
