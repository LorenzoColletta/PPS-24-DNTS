package domain.dataset

import domain.data.Label.*
import domain.data.LabeledPoint2D
import domain.util.{Noise, Space}

object DatasetGenerator:

  def generate(
    size: Int,
    model: LabeledDatasetModel
  ): List[LabeledPoint2D] =
    val half = size / 2
    val labels = List.fill(half)(Positive) ++ List.fill(size - half)(Negative)
    labels.map(model.sample)

extension (points: List[LabeledPoint2D])
  def withNoise(noise: Noise)(using Space): List[LabeledPoint2D] =
    points.map { case LabeledPoint2D(point, label) =>
      LabeledPoint2D(noise(point), label)
    }
