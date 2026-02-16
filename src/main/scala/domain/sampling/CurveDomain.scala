package domain.sampling

import domain.util.Space

/**
 * A close interval of real values that represents the domain of a curve.
 *
 * @param min the lower bound of the domain
 * @param max the upper bound of the domain
 */
final case class Domain(min: Double, max: Double)

/**
 * Type class responsible for the computation of the domain of a given curve type C.
 */
trait CurveDomainCalculator[C]:

  /**
   * Computes the valid domain of a curve.
   *
   * @param curve the curve whose domain is to be calculated
   * @param space the space given
   * @return the interval of valid values
   */
  def domain(curve: C)(using Space): Domain
