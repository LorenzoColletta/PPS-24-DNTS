package domain.sampling

import domain.pattern.SpiralCurve
import domain.util.Space

import scala.math.abs

object SpiralDomain extends CurveDomainCalculator[SpiralCurve]:

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
