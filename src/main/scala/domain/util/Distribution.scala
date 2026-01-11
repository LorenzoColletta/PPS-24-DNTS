package domain.util

import domain.sampling.Domain
import scala.util.Random
import scala.annotation.tailrec

trait Distribution:
  def sample(domain: Domain): Double


final class UniformDistribution(seed: Option[Long] = None) extends Distribution:

  private val random = seed match
    case Some(s) => new Random(s)
    case None    => new Random()

  override def sample(domain: Domain): Double =
    random.between(domain.min, domain.max)


final class NormalDistribution(
  mean: Double,
  sigma: Double,
  seed: Option[Long] = None, 
  maxIterations: Long = 100
) extends Distribution:

  private val rng = seed match
    case Some(s) => new Random(s)
    case None    => new Random()

  override def sample(domain: Domain): Double =

    @tailrec
    def loop(maxIterations: Long): Double =
      val x = rng.nextGaussian() * sigma + mean
      if x >= domain.min && x <= domain.max || maxIterations == 0 then x
      else loop( maxIterations - 1)

    val x = loop(maxIterations)

    if x < domain.min then domain.min
    else if x > domain.max then domain.max
    else x
