package domain.util

import domain.data.Point2D
import domain.sampling.Domain

trait Noise:
  def apply(p: Point2D)(using Space): Point2D

final case class NoiseWithDistribution(distribution: Distribution, domain: Domain) extends Noise:

  override def apply(p: Point2D)(using space: Space): Point2D =
    val dx = distribution.sample(domain)
    val dy = distribution.sample(domain)

    space.clamp(
      Point2D(p.x + dx, p.y + dy)
    )
