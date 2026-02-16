package domain.data.sampling

import domain.data.Point2D

trait PointSampler:
  def sample(): Point2D
