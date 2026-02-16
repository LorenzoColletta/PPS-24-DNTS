package domain.dataset

import domain.pattern.GaussianCluster
import domain.sampling.{DistributionSampler, Domain}

/**
 * A binary dataset composed of two Gaussian clusters.
 *
 * The clusters are symmetrically placed with respect to the origin,
 * separated by the given distance.
 *
 * @param distance     the distance between the centers of the two clusters
 * @param sigma        the standard deviation of each Gaussian distribution
 * @param domain       the bounding domain of the space
 * @param radius       the maximum sampling radius from each cluster center
 * @param seedPositive optional seed for the positive sampler
 * @param seedNegative optional seed for the negative sampler
 */
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

