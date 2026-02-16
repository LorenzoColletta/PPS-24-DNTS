package domain.data.util

import domain.data.Point2D

final case class Space(width: Double, height: Double) {
  require(width > 0, "Width must be positive")
  require(height > 0, "Height must be positive")
}

extension (space: Space)
  def clamp(p: Point2D): Point2D =
    Point2D(
      x = p.x.max(-space.width /2).min(space.width /2),
      y = p.y.max(-space.height /2).min(space.height /2)
    )