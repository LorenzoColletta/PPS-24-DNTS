package domain.data.pattern

import domain.data.Point2D

trait PointDistribution:
  def sample(): Point2D
