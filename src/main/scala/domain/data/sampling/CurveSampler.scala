package domain.data.sampling

import domain.data.Point2D
import domain.data.pattern.{ParametricCurve, SpiralCurve}
import domain.data.util.{Distribution, Space}

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