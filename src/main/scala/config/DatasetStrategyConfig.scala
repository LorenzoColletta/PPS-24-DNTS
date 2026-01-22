package config

/**
 * Represent the available data generation strategies.
 * Each implementation encapsulates only the parameters strictly required for that specific dataset type.
 */
sealed trait DatasetStrategyConfig

object DatasetStrategyConfig:

  /**
   * Configuration for the [[DoubleGaussianDataset]].
   * Defines two gaussian distributions within an explicit domain.
   *
   * @param distance   The distance between the two distributions.
   * @param sigma      The standard deviation of the distributions. Must be > 0.
   * @param radius     The effective radius for point generation. Must be > 0.
   * @param domainMin  The lower bound of the generation domain.
   * @param domainMax  The upper bound of the generation domain.
   */
  case class Gaussian(
    distance: Double,
    sigma: Double,
    radius: Double,
    domainMin: Double,
    domainMax: Double
  ) extends DatasetStrategyConfig {
    require(sigma > 0, s"Sigma must be positive. Found: $sigma")
    require(radius > 0, s"Radius must be positive. Found: $radius")
    require(domainMax > domainMin, s"Domain Max must be greater than Min. Found: [$domainMin, $domainMax]")
  }

  /**
   * Configuration for the [[DoubleRingDataset]].
   * Defines ring structures with explicit center coordinates and dimensions.
   *
   * @param centerX      The X-coordinate of the ring's center.
   * @param centerY      The Y-coordinate of the ring's center.
   * @param innerRadius  The radius of the inner edge. Must be >= 0.
   * @param outerRadius  The radius of the outer edge. Must be > innerRadius.
   * @param thickness    The width/thickness of the ring. Must be > 0.
   * @param domainMin    The lower bound of the generation domain.
   * @param domainMax    The upper bound of the generation domain.
   */
  case class Ring(
    centerX: Double,
    centerY: Double,
    innerRadius: Double,
    outerRadius: Double,
    thickness: Double,
    domainMin: Double,
    domainMax: Double
  ) extends DatasetStrategyConfig {
    require(innerRadius >= 0, s"Inner radius must be >= 0. Found: $innerRadius")
    require(outerRadius > innerRadius, s"Outer radius must be greater than inner radius ($innerRadius). Found: $outerRadius")
    require(thickness > 0, s"Thickness must be positive. Found: $thickness")
    require(domainMax > domainMin, s"Domain max must be greater than min. Found: [$domainMin, $domainMax]")
  }

  /**
   * Configuration for the [[DoubleSpiralDataset]].
   * This strategy relies on an implicit Space definition and does not require explicit domain bounds.
   *
   * @param startDistance   The initial distance from the origin. Must be >= 0.
   * @param branchDistance  The distance between the spiral arms.
   * @param rotation        The rotation angle factor.
   */
  case class Spiral(
    startDistance: Double,
    branchDistance: Double,
    rotation: Double
  ) extends DatasetStrategyConfig {
    require(startDistance >= 0, s"Start distance must be >= 0. Found: $startDistance")
  }

  /**
   * Configuration for the [[DoubleXorDataset]].
   * Generates a non-linearly separable pattern within an explicit domain.
   *
   * @param domainMin  The lower bound of the generation domain.
   * @param domainMax  The upper bound of the generation domain.
   */
  case class Xor(
    domainMin: Double,
    domainMax: Double
  ) extends DatasetStrategyConfig {
    require(domainMax > domainMin, s"Domain max must be greater than min. Found: [$domainMin, $domainMax]")
  }
