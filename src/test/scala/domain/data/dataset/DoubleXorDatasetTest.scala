package domain.data.dataset

import domain.data.*
import domain.data.dataset.DoubleXorDataset
import domain.data.pattern.Quadrant
import domain.data.sampling.Domain
import domain.data.util.Space
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class DoubleXorDatasetTest extends AnyFunSuite with Matchers:

  private val domain = Domain(0.0, 10.0)
  private val dataset = new DoubleXorDataset(domain, seedPositive = Some(42), seedNegative = Some(1337))

  given Space = Space(100.0, 100.0)

  private def quadrantOf(p: Point2D): Quadrant =
    (p.x > 0, p.y > 0) match
      case (true, true)   => Quadrant.I
      case (false, true)  => Quadrant.II
      case (false, false) => Quadrant.III
      case (true, false)  => Quadrant.IV

  test("DoubleXorDataset should generate positive points in Quadrant I or III"):
    val samples = List.fill(20)(dataset.sample(Label.Positive))

    samples.foreach { case LabeledPoint2D(p, label) =>
      label shouldBe Label.Positive
      quadrantOf(p) should (equal(Quadrant.I) or equal(Quadrant.III))
    }

  test("DoubleXorDataset should generate negative points in Quadrant II or IV"):
    val samples = List.fill(20)(dataset.sample(Label.Negative))

    samples.foreach { case LabeledPoint2D(p, label) =>
      label shouldBe Label.Negative
      quadrantOf(p) should (equal(Quadrant.II) or equal(Quadrant.IV))
    }

  test("DoubleXorDataset should preserve dataset size"):
    val positives = List.fill(10)(dataset.sample(Label.Positive))
    val negatives = List.fill(15)(dataset.sample(Label.Negative))
    (positives.size + negatives.size) shouldBe 25

  test("DoubleXorDataset should generate reproducible points with the same seed"):
    val ds1 = new DoubleXorDataset(domain, seedPositive = Some(123), seedNegative = Some(456))
    val ds2 = new DoubleXorDataset(domain, seedPositive = Some(123), seedNegative = Some(456))

    val p1 = List.fill(5)(ds1.sample(Label.Positive)).map(_.point)
    val p2 = List.fill(5)(ds2.sample(Label.Positive)).map(_.point)

    p1 shouldBe p2

    val n1 = List.fill(5)(ds1.sample(Label.Negative)).map(_.point)
    val n2 = List.fill(5)(ds2.sample(Label.Negative)).map(_.point)

    n1 shouldBe n2
