package domain.data.util

import domain.data.sampling.Domain
import org.scalatest.funsuite.AnyFunSuite

class DistributionTest extends AnyFunSuite:

  test("uniform distribution samples always within domain"):
    val domain = Domain(0.0, 1.0)
    val dist   = UniformDistribution()

    val samples = List.fill(10_000)(dist.sample(domain))

    assert(samples.forall(x => x >= domain.min && x <= domain.max))

  test("uniform distribution with same seed produces same sequence"):
    val domain = Domain(-1.0, 2.0)

    val d1 = UniformDistribution(Some(42L))
    val d2 = UniformDistribution(Some(42L))

    val s1 = List.fill(100)(d1.sample(domain))
    val s2 = List.fill(100)(d2.sample(domain))

    assert(s1 == s2)

  test("uniform distribution with different seeds produces different sequences"):
    val domain = Domain(0.0, 1.0)

    val d1 = UniformDistribution(Some(1L))
    val d2 = UniformDistribution(Some(2L))

    val s1 = List.fill(50)(d1.sample(domain))
    val s2 = List.fill(50)(d2.sample(domain))

    assert(s1 != s2)

  test("normal distribution samples always within domain") {
    val domain = Domain(0.0, 1.0)
    val dist   = NormalDistribution(
      mean = 0.5,
      sigma = 0.1
    )

    val samples = List.fill(10_000)(dist.sample(domain))

    assert(samples.forall(x => x >= domain.min && x <= domain.max))
  }

  test("normal distribution with same seed produces same sequence") {
    val domain = Domain(0.0, 1.0)

    val d1 = NormalDistribution(0.5, 0.1, Some(123L))
    val d2 = NormalDistribution(0.5, 0.1, Some(123L))

    val s1 = List.fill(200)(d1.sample(domain))
    val s2 = List.fill(200)(d2.sample(domain))

    assert(s1 == s2)
  }

  test("normal distribution samples are centered around the mean") {
    val domain = Domain(0.0, 1.0)
    val mean   = 0.5
    val sigma  = 0.1

    val dist = NormalDistribution(mean, sigma, Some(99L))

    val samples = List.fill(20_000)(dist.sample(domain))
    val avg     = samples.sum / samples.size

    assert(math.abs(avg - mean) < 0.02)
  }
