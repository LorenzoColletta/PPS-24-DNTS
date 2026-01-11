package domain.sampling

import domain.util.Space

final case class Domain(min: Double, max: Double)

trait CurveDomainCalculator[C]:
  def domain(curve: C)(using Space): Domain
