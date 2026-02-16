package domain.data.pattern

import domain.data.Point2D
import domain.data.sampling.Domain
import domain.data.util.UniformDistribution

enum Quadrant:
  case I, II, III, IV

final case class UniformSquare(
  domain: Domain,
  quadrant: Quadrant,
  seed: Option[Long] = None
) extends PointDistribution:

  private val distribution = UniformDistribution(seed)

  override def sample(): Point2D =
    val xRaw = distribution.sample(Domain(0, domain.max))
    val yRaw = distribution.sample(Domain(0, domain.max))

    val (x, y) = quadrant match
      case Quadrant.I   => (xRaw, yRaw)
      case Quadrant.II  => (-xRaw, yRaw)
      case Quadrant.III => (-xRaw, -yRaw)
      case Quadrant.IV  => (xRaw, -yRaw)

    Point2D(x, y)
