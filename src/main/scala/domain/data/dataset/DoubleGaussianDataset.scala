package domain.data.dataset

import domain.data.pattern.GaussianCluster
import domain.data.sampling.{DistributionSampler, Domain}

final class DoubleGaussianDataset(
  distance: Double,
  sigma: Double,
  domain: Domain,
  radius: Double,
  seedPositive: Option[Long] = None,
  seedNegative: Option[Long] = None
) extends BinaryDataset(
  positive = DistributionSampler(GaussianCluster(distance / 2, distance / 2, sigma, domain, radius, seedPositive)),
  negative = DistributionSampler(GaussianCluster(-distance / 2, -distance / 2, sigma, domain, radius, seedNegative))
)

