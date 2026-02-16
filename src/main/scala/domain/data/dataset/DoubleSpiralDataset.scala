package domain.data.dataset

import domain.data.pattern.SpiralCurve
import domain.data.sampling.CurveSampler
import domain.data.util.{Space, UniformDistribution}

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

