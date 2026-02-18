package domain.data.dataset

import domain.data.Label.*
import domain.data.LabeledPoint2D
import domain.data.util.{Noise, Space}

import scala.util.Random

/**
 * Utility object for generating labeled 2D datasets.
 *
 * It creates balanced binary datasets by sampling points
 * from a given [[LabeledDatasetModel]].
 */
object DatasetGenerator:

  /**
   * Generates a labeled dataset of the given size.
   *
   * The dataset is balanced: half of the samples are labeled
   * [[Positive]] and the remaining ones [[Negative]].
   *
   * @param size  the total number of samples to generate
   * @param model the dataset model used to sample labeled points
   * @return a list of labeled 2D points
   */
  def generate(
    size: Int,
    model: LabeledDatasetModel
  ): List[LabeledPoint2D] =
    val half = size / 2
    val labels = List.fill(half)(Positive) ++ List.fill(size - half)(Negative)
    labels.map(model.sample)

extension (points: List[LabeledPoint2D])

  /**
   * Applies noise to all points in the dataset while preserving their labels.
   *
   * @param noise the noise transformation applied to each point
   * @param space the space in which the noise is applied
   * @return a new list of noisy labeled points
   */
  def withNoise(noise: Noise)(using Space): List[LabeledPoint2D] =
    points.map { case LabeledPoint2D(point, label) =>
      LabeledPoint2D(noise(point), label)
    }

  def shuffle(seed: Option[Long]): List[LabeledPoint2D] =
    val rand = seed.map(s => new Random(s)).getOrElse(new Random())
    rand.shuffle(points)
