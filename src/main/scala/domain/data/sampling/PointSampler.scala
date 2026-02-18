package domain.data.sampling

import domain.data.Point2D

/**
 * Abstraction of a point generator.
 */
trait PointSampler:

  /**
   * Generates a [[Point2D]] by sampling the internal implementation strategy.
   * @return a 2d point
   */
  def sample(): Point2D
