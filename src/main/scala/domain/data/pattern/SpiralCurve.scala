package domain.data.pattern

import domain.data.Point2D

import scala.math.{abs, cos, sin}

/**
 * Implements a parametric Archimedean spiral in a 2d space.
 *
 * @param startDistance  the initial distance form the origin
 * @param branchDistance the radius increment per parameter unit
 * @param rotation       the global angular rotation of the spiral (radians)
 */
case class SpiralCurve (
          startDistance: Double,
          branchDistance: Double,
          rotation: Double,
        ) extends ParametricCurve:

  /**
   * Computes the 2d point along the spiral given an 'x' parameter.
   *
   * @param x the parameter
   * @return the corresponding point
   */
  override def at(x: Double): Point2D =
    val radius = startDistance + branchDistance * x
    val polarAngle = abs(x) + rotation
    Point2D(
      radius * cos(polarAngle),
      radius * sin(polarAngle)
    )




