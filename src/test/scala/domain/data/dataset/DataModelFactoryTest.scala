package domain.data.dataset

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import config.DatasetStrategyConfig
import domain.data.util.Space

class DataModelFactoryTest extends AnyFunSuite with Matchers:

  given Space = Space(100, 100)

  test("Factory should create a DoubleGaussianDataset instance when Strategy is Gaussian"):
    val conf = DatasetStrategyConfig.Gaussian(2.0, 0.5, 1.0, -5, 5)
    val dataset = DataModelFactory.create(conf, Some(123L))

    dataset shouldBe a [DoubleGaussianDataset]

  test("Factory should create a DoubleSpiralDataset instance when Strategy is Spiral"):
    val conf = DatasetStrategyConfig.Spiral(0.0, 0.5, 0.0)
    val dataset = DataModelFactory.create(conf, Some(123L))

    dataset shouldBe a [DoubleSpiralDataset]

  test("Factory should create a DoubleRingDataset instance when Strategy is Ring"):
    val conf = DatasetStrategyConfig.Ring(0, 0, 5, 10, 2, -20, 20)
    val dataset = DataModelFactory.create(conf, Some(123L))

    dataset shouldBe a [DoubleRingDataset]

  test("Factory should create a DoubleXorDataset instance when Strategy is Xor"):
    val conf = DatasetStrategyConfig.Xor(-10, 10)
    val dataset = DataModelFactory.create(conf, Some(123L))

    dataset shouldBe a [DoubleXorDataset]
    