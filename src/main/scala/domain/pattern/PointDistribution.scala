package domain.pattern

import domain.data.Point2D

trait PointDistribution:
  def sample(): Point2D
