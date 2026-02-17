package domain.data.dataset

import domain.data.pattern.SpiralCurve
import domain.data.sampling.CurveSampler
import domain.data.util.{Space, UniformDistribution}

/**
 * Binary dataset composed of two spirals.
 *
 * The positive class is generated using the given spiral,
 * the negative class is generated using the given spiral rotated of π radians.
 *
 * @param curve        the spiral
 * @param seedPositive optional seed for the positive class
 * @param seedNegative optional seed for the negative class
 * @param space        given available space
 */
final class DoubleSpiralDataset(
  curve: SpiralCurve,
  seedPositive: Option[Long] = None,
  seedNegative: Option[Long] = None
)(using space: Space)
  extends BinaryDataset(
    positive = CurveSampler.spiralSampler(curve, new UniformDistribution(seedPositive)),
    negative = CurveSampler.spiralSampler(curve.copy(
      rotation = curve.rotation + Math.PI),
      new UniformDistribution(seedNegative)
    )
  )

