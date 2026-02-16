package domain.sampling

import domain.data.Point2D
import domain.pattern.{Quadrant, UniformSquare}

import scala.util.Random

/**
 * Implements [[PointSampler]], it generates 2d points using [[UniformSquare]] pattern in two given quadrants.
 *
 * @param domain the interval of valid values
 * @param quadrants the areas in which the points are generated
 * @param seed the seed for random numeber generation
 */
final case class XorSampler(
  domain: Domain,
  quadrants: (Quadrant, Quadrant),
  seed: Option[Long] = None
) extends PointSampler:

  private val rand = seed match
    case Some(s) => new Random(s)
    case None    => new Random()

  private val pattern1 = UniformSquare(domain, quadrants._1, seed.map(_ + 0))
  private val pattern2 = UniformSquare(domain, quadrants._2, seed.map(_ + 1))

  /**
   * Generates a 2d point in one of the two quadrants.
   * @return a 2d point
   */
  override def sample(): Point2D =
    if rand.nextBoolean() then pattern1.sample() else pattern2.sample()
