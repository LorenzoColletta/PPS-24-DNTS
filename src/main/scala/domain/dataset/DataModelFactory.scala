package domain.dataset

import config.DatasetStrategyConfig
import config.DatasetStrategyConfig.*
import domain.util.Space
import domain.dataset.*
import domain.sampling.*
import domain.data.*
import domain.pattern.*

/**
 * Factory responsible for instantiating concrete [[LabeledDatasetModel]] implementations
 * based on the provided configuration strategy.
 */
object DataModelFactory:

  /**
   * Creates a fully configured [[LabeledDatasetModel]] based on the specific strategy and random seed.
   *
   * @param strategy   The specific configuration strategy.
   * @param globalSeed An optional master seed used to derive random sub-seeds.
   * @param space      The implicit [[Space]] definition required by the underlying generators.
   * @return A ready-to-use instance of [[LabeledDatasetModel]].
   */
  def create(
    strategy: DatasetStrategyConfig,
    globalSeed: Option[Long]
  )(using space: Space): LabeledDatasetModel =
  
    val seedPos = globalSeed
    val seedNeg = globalSeed.map(_ + 1)

    strategy match
      case Gaussian(dist, sigma, radius, min, max) =>
        new DoubleGaussianDataset(
          distance = dist,
          sigma = sigma,
          domain = Domain(min, max),
          radius = radius,
          seedPositive = seedPos,
          seedNegative = seedNeg
        )

      case Ring(cx, cy, inR, outR, thick, min, max) =>
        new DoubleRingDataset(
          center = Point2D(cx, cy),
          innerRadius = inR,
          outerRadius = outR,
          thickness = thick,
          domain = Domain(min, max),
          seedPositive = seedPos,
          seedNegative = seedNeg
        )

      case Spiral(startDist, branchDist, rot) =>
        val curve = SpiralCurve(startDist, branchDist, rot)

        new DoubleSpiralDataset(
          curve = curve,
          seedPositive = seedPos,
          seedNegative = seedNeg
        )

      case Xor(min, max) =>
        new DoubleXorDataset(
          domain = Domain(min, max),
          seedPositive = seedPos,
          seedNegative = seedNeg
        )
