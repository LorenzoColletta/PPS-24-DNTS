package domain.dataset

import domain.data.Label.{Negative, Positive}
import domain.data.{Label, LabeledPoint2D}
import domain.sampling.PointSampler

/**
 * A binary dataset model that generates labeled points
 * using two distinct point samplers:
 * one for positive samples and one for negative samples.
 *
 * @param positive the sampler used to generate positive points
 * @param negative the sampler used to generate negative points
 */
class BinaryDataset(
   positive: PointSampler,
   negative: PointSampler
) extends LabeledDatasetModel:

  /**
   * Generates a labeled point according to the given label.
   *
   * @param label the label determining which sampler is used
   * @return a labeled 2D point
   */
  override def sample(label: Label): LabeledPoint2D =
    label match
      case Positive => LabeledPoint2D(positive.sample(), Positive)
      case Negative => LabeledPoint2D(negative.sample(), Negative)

