package domain.data.dataset

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import domain.data.Label
import domain.data.dataset.DoubleGaussianDataset
import domain.data.sampling.Domain

class DoubleGaussianDatasetTest extends AnyFunSuite with Matchers:
  test("doubleGaussian should produce labeled samples with correct labels"):

    val dataset = DoubleGaussianDataset(
      distance = 10.0,
      sigma = 0.1,
      domain = Domain(-20.0, 20.0),
      radius = 5.0,
      Some(42L)
    )

    val pos = dataset.sample(Label.Positive)
    val neg = dataset.sample(Label.Negative)

    pos.label shouldBe Label.Positive
    neg.label shouldBe Label.Negative

  test("doubleGaussian positive and negative clusters should be separated"):

    val distance = 10.0

    val dataset = DoubleGaussianDataset(
      distance = distance,
      sigma = 0.1,
      domain = Domain(-20.0, 20.0),
      radius = 5.0,
      Some(123L)
    )

    val pos = dataset.sample(Label.Positive).point
    val neg = dataset.sample(Label.Negative).point

    pos.x should be > 0.0
    neg.x should be < 0.0
