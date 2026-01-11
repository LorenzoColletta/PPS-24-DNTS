package domain.pattern

import domain.data.Point2D

trait ParametricCurve:
  def at(x: Double): Point2D
