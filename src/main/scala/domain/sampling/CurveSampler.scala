package domain.sampling

import domain.data.Point2D
import domain.pattern.{ParametricCurve, SpiralCurve}
import domain.sampling.SpiralDomain
import domain.util.{Distribution, Space}

private final case class CurveSampler(
  curve: ParametricCurve,
  parameterDistribution: () => Double
) extends PointSampler:

  override def sample(): Point2D = curve.at(parameterDistribution())

object CurveSampler:

  def spiralSampler(
    curve: SpiralCurve,
    distribution: Distribution
  )(using Space): CurveSampler =
    val domain = SpiralDomain.domain(curve)
    CurveSampler(
      curve,
      () => distribution.sample(domain)
    )