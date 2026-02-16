package domain.util

import domain.sampling.Domain
import scala.util.Random
import scala.annotation.tailrec

/**
 * Represents a probability distribution capable of generating
 * scalar values within a given numeric domain.
 *
 * Implementations define how samples are generated.
 */
trait Distribution:

  /**
   * Generates a random value constrained to the given domain.
   *
   * @param domain the interval in which the value must lie
   * @return a sampled value
   */
  def sample(domain: Domain): Double


/**
 * Uniform distribution over a given numeric domain.
 *
 * Values are sampled with constant probability
 * between the domain bounds.
 *
 * @param seed optional seed for reproducible randomness
 */
final class UniformDistribution(seed: Option[Long] = None) extends Distribution:

  private val random = seed match
    case Some(s) => new Random(s)
    case None    => new Random()

  /**
   * Samples a value uniformly within the given domain.
   *
   * @param domain the interval defining the sampling bounds
   * @return a uniformly distributed value
   */
  override def sample(domain: Domain): Double =
    random.between(domain.min, domain.max)

/**
 * Truncated normal (Gaussian) distribution.
 *
 * Values are sampled from a Gaussian distribution with the given
 * mean and standard deviation, and constrained to the provided domain.
 *
 * If a valid value is not generated within `maxIterations`,
 * the result is clamped to the nearest boundary.
 *
 * @param mean          the mean of the distribution
 * @param sigma         the standard deviation
 * @param seed          optional seed for random number generation
 * @param maxIterations maximum attempts before fallback clamping
 */
final class NormalDistribution(
  mean: Double,
  sigma: Double,
  seed: Option[Long] = None, 
  maxIterations: Long = 100
) extends Distribution:

  private val rng = seed match
    case Some(s) => new Random(s)
    case None    => new Random()

  /**
   * Samples a Gaussian value constrained to the given domain.
   *
   * @param domain the interval defining valid values
   * @return a sampled value within the domain
   */
  override def sample(domain: Domain): Double =

    @tailrec
    def loop(maxIterations: Long): Double =
      val x = rng.nextGaussian() * sigma + mean
      if x >= domain.min && x <= domain.max || maxIterations == 0 then x
      else loop( maxIterations - 1)

    val x = loop(maxIterations)

    if x < domain.min then domain.min
    else if x > domain.max then domain.max
    else x
