package domain.pattern

import domain.data.Point2D

import scala.math.{abs, cos, sin}

case class SpiralCurve (
          startDistance: Double,
          branchDistance: Double,
          rotation: Double,
        ) extends ParametricCurve:

  override def at(x: Double): Point2D =
    val radius = startDistance + branchDistance * x
    val polarAngle = abs(x) + rotation
    Point2D(
      radius * cos(polarAngle),
      radius * sin(polarAngle)
    )




