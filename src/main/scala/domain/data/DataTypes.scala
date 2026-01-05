package domain.data

import domain.data.LinearAlgebra.Vector

enum Label:
  case Positive
  case Negative


case class Point2D(x: Double, y: Double)

case class LabeledPoint2D(point: Point2D, label: Label)


extension (l: Label)
  def toVector: Vector = l match
    case Label.Positive => Vector(1.0)
    case Label.Negative => Vector(0.0)
