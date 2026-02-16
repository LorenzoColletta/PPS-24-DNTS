package domain.pattern

import domain.data.Point2D

/**
 * Defines a strategy for generating 2d points according to a
 * geometric pattern or a statistical pattern.
 */
trait PointDistribution:

  /**
   * Generates a 2D point.
   * @return the generated point
   */
  def sample(): Point2D
