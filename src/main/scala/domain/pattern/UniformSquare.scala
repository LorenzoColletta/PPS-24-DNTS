package domain.pattern

import domain.data.Point2D
import domain.sampling.Domain
import domain.util.UniformDistribution

enum Quadrant:
  case I, II, III, IV

/**
 * Generates 2d points uniformly in a selected quadrant of the cartesian plane.
 *
 * @param domain   domain of valid values
 * @param quadrant quadrante cartesiano in cui generare i punti
 * @param seed     seed opzionale per rendere la distribuzione deterministica
 */
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
