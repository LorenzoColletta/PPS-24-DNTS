package domain.data

import domain.data.LinearAlgebra.Vector

/**
 * Represents the target classification category for binary problems.
 */
enum Label:
  case Positive
  case Negative

extension (l: Label)
  /**
   * Converts the semantic label into a numerical vector suitable for linear algebra operations.
   * Mapping: Positive -> [1.0], Negative -> [0.0].
   */
  def toVector: Vector = l match
    case Label.Positive => Vector(1.0)
    case Label.Negative => Vector(0.0)


/**
 * Representation of a point in 2-dimensional space.
 *
 * @param x The coordinate on the abscissa axis.
 * @param y The coordinate on the ordinate axis.
 */
case class Point2D(x: Double, y: Double)

/**
 * Represents a data 2D point associated with its specific classification.
 *
 * @param point The geometric position of the sample.
 * @param label The class to which this point belongs.
 */
case class LabeledPoint2D(point: Point2D, label: Label)
