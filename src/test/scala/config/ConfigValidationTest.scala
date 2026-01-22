package config

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import domain.network.{Activations, Feature, HyperParams, LayerConf, Regularization}

class ConfigValidationTest extends AnyFunSuite with Matchers:

  test("DatasetStrategyConfig.Gaussian should throw IllegalArgumentException if sigma is negative"):
    an [IllegalArgumentException] should be thrownBy {
      DatasetStrategyConfig.Gaussian(distance = 1, sigma = -0.5, radius = 1, domainMin = -1, domainMax = 1)
    }

  test("DatasetStrategyConfig.Ring should throw IllegalArgumentException if innerRadius >= outerRadius"):
    an [IllegalArgumentException] should be thrownBy {
      DatasetStrategyConfig.Ring(0, 0, innerRadius = 5, outerRadius = 3, thickness = 1, domainMin = -10, domainMax = 10)
    }

  test("DatasetStrategyConfig.Xor should throw IllegalArgumentException if domainMin >= domainMax"):
    an [IllegalArgumentException] should be thrownBy {
      DatasetStrategyConfig.Xor(domainMin = 10, domainMax = -10)
    }

  test("DatasetStrategyConfig.Spiral should throw IllegalArgumentException for negative start distance"):
    an [IllegalArgumentException] should be thrownBy {
      DatasetStrategyConfig.Spiral(startDistance = -1.0, branchDistance = 1.0, rotation = 0)
    }