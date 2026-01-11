package domain.dataset

import domain.data.Label.{Negative, Positive}
import domain.data.{Label, LabeledPoint2D}
import domain.sampling.PointSampler

class BinaryDataset(
   positive: PointSampler,
   negative: PointSampler
) extends LabeledDatasetModel:

  override def sample(label: Label): LabeledPoint2D =
    label match
      case Positive => LabeledPoint2D(positive.sample(), Positive)
      case Negative => LabeledPoint2D(negative.sample(), Negative)

