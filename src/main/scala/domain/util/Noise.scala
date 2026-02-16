package domain.util

import domain.data.Point2D
import domain.sampling.Domain

/**
 * Represents a transformation that perturbs a 2D point.
 *
 * Noise implementations modify point coordinates while
 * respecting the bounds of the given [[Space]].
 */
trait Noise:

  /**
   * Applies noise to a point.
   *
   * @param p     the original point
   * @param space the space in which the point is defined
   * @return the perturbed point
   */
  def apply(p: Point2D)(using Space): Point2D

/**
 * Noise implementation based on a scalar distribution.
 *
 * Independent noise values are sampled for the x and y coordinates
 * and added to the original point. The resulting point is then
 * clamped within the given space.
 *
 * @param distribution the distribution used to generate coordinate offsets
 * @param domain       the domain from which offsets are sampled
 */
final case class NoiseWithDistribution(distribution: Distribution, domain: Domain) extends Noise:

  /**
   * Applies additive noise to both coordinates of the point.
   *
   * @param p     the original point
   * @param space the space in which clamping is enforced
   * @return the noisy and clamped point
   */
  override def apply(p: Point2D)(using space: Space): Point2D =
    val dx = distribution.sample(domain)
    val dy = distribution.sample(domain)

    space.clamp(
      Point2D(p.x + dx, p.y + dy)
    )
