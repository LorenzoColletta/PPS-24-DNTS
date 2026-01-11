package domain.dataset

import domain.pattern.SpiralCurve
import domain.sampling.CurveSampler
import domain.util.{Space, UniformDistribution}

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

