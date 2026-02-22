package domain.data.dataset

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import domain.data.Label
import domain.data.pattern.SpiralCurve
import domain.data.util.Space


class DoubleSpiralDatasetTest extends AnyFunSuite with Matchers:
  given Space = Space(width = 100.0, height = 100.0)

  test("doubleSpiral should produce labeled samples with correct labels"):

    val curve = SpiralCurve(
      startDistance = 1.0,
      branchDistance = 1.0,
      rotation = 0.0
    )

    val dataset = DoubleSpiralDataset(curve, Some(42L))

    val pos = dataset.sample(Label.Positive)
    val neg = dataset.sample(Label.Negative)

    pos.label shouldBe Label.Positive
    neg.label shouldBe Label.Negative


  test("doubleSpiral positive and negative samples should differ"):

    val curve = SpiralCurve(
      startDistance = 1.0,
      branchDistance = 1.0,
      rotation = 0.0
    )

    val dataset = DoubleSpiralDataset(curve, Some(123L))

    val pos = dataset.sample(Label.Positive)
    val neg = dataset.sample(Label.Negative)

    pos.point should not be neg.point


  test("doubleSpiral should be deterministic with the same seed"):

    val curve = SpiralCurve(1.0, 1.0, 0.0)

    val ds1 = DoubleSpiralDataset(curve, Some(99L))
    val ds2 = DoubleSpiralDataset(curve, Some(99L))

    val samples1 = (1 to 5).map(_ => ds1.sample(Label.Positive))
    val samples2 = (1 to 5).map(_ => ds2.sample(Label.Positive))

    samples1 shouldBe samples2
