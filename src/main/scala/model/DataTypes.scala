package model

enum Label:
  case Positive
  case Negative

case class Point2D(x: Double, y: Double)

case class LabeledPoint2D(point: Point2D, label: Label)

