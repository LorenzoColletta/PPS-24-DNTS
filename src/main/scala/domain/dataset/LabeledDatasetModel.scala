package domain.dataset

import domain.data.{Label, LabeledPoint2D}

/**
 * Represents a model capable of generating labeled 2D points.
 */
trait LabeledDatasetModel:

  /**
   * Generates a labeled point corresponding to the given label.
   *
   * @param label the desired label
   * @return a 2D point associated with the label
   */
  def sample(label: Label): LabeledPoint2D