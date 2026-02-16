package domain.pattern

import domain.data.Point2D

/**
 * Represents a 2D parametric curve defined as a function that associates
 * a real parameter 'x' to a point in a 2D space.
 */
trait ParametricCurve:

  /**
   * Gets the 2d point corresponding to the given 'x' parameter.
   *
   * @param x the parameter value
   * @return the corresponding 2d point
   */
  def at(x: Double): Point2D
