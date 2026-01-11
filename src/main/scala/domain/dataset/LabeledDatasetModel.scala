package domain.dataset

import domain.data.{Label, LabeledPoint2D}

trait LabeledDatasetModel:
  def sample(label: Label): LabeledPoint2D