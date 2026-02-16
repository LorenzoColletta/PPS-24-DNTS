package domain.util

import domain.data.Point2D

/**
 * Represents a 2D rectangular space centered at the origin.
 *
 * The space spans:
 *  - x ∈ [-width/2,  width/2]
 *  - y ∈ [-height/2, height/2]
 *
 * @param width  the total width of the space
 * @param height the total height of the space
 */
final case class Space(width: Double, height: Double) {
  require(width > 0, "Width must be positive")
  require(height > 0, "Height must be positive")
}

/**
 * Clamps a point so that it lies within the space boundaries.
 *
 * @param p the point to clamp
 * @return a point guaranteed to lie inside the space
 */
extension (space: Space)
  def clamp(p: Point2D): Point2D =
    Point2D(
      x = p.x.max(-space.width /2).min(space.width /2),
      y = p.y.max(-space.height /2).min(space.height /2)
    )