package domain.data.sampling

import domain.data.pattern.SpiralCurve
import domain.data.util.Space

import scala.math.abs

/**
 * Computes the parameter domain of a [[SpiralCurve]] in a finite [[Space]].
 */
object SpiralDomain extends CurveDomainCalculator[SpiralCurve]:

  /**
   * Computes the valid interval for the parameters of a [[SpiralCurve]] in a finite [[space]].
   *
   * @param curve the spiral to compute the domain to
   * @param space the available space
   * @return the valid interval of parameters value
   */
  override def domain(curve: SpiralCurve)(using space: Space): Domain =
    val minScale = math.min(space.width, space.height)
    val angle = (minScale - curve.startDistance) / curve.branchDistance
    val maxT = abs(math.min(
      minScale * math.cos(angle),
      minScale * math.sin(angle)
    ))

    if maxT > space.width then
      Domain(-space.width, space.width)
    else
      Domain(0.0, maxT)
