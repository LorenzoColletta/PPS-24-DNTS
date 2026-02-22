package domain.data.dataset

import domain.data.*
import domain.data.sampling.Domain
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class DoubleRingDatasetTest extends AnyFunSuite with Matchers:

  private val domain = Domain(-100.0, 100.0)
  private val center = Point2D(0.0, 0.0)
  private val innerRadius = 10.0
  private val outerRadius = 30.0
  private val thickness = 5.0

  private val dataset = new DoubleRingDataset(
    center,
    innerRadius,
    outerRadius,
    thickness,
    domain,
    seedPositive = Some(42),
    seedNegative = Some(1337)
  )

  test("DoubleRingDataset should generate positive points within inner radius"):
    val sample = dataset.sample(Label.Positive)
    val dx = sample.point.x - center.x
    val dy = sample.point.y - center.y
    val dist = math.sqrt(dx*dx + dy*dy)

    dist should be >= 0.0
    dist should be <= innerRadius
    sample.label shouldBe Label.Positive


  test("DoubleRingDataset should generate negative points within outer ring"):
    val sample = dataset.sample(Label.Negative)
    val dx = sample.point.x - center.x
    val dy = sample.point.y - center.y
    val dist = math.sqrt(dx*dx + dy*dy)

    dist should be >= outerRadius
    dist should be <= (outerRadius + thickness)
    sample.label shouldBe Label.Negative


  test("DoubleRingDataset should generate multiple points correctly"):
    val samples = List.fill(20)(dataset.sample(Label.Positive)) ++
      List.fill(20)(dataset.sample(Label.Negative))

    samples.count(_.label == Label.Positive) shouldBe 20
    samples.count(_.label == Label.Negative) shouldBe 20

    samples.foreach {
      case LabeledPoint2D(p, Label.Positive) =>
        val d = math.sqrt((p.x - center.x)*(p.x - center.x) + (p.y - center.y)*(p.y - center.y))
        d should (be >= 0.0 and be <= innerRadius)
      case LabeledPoint2D(p, Label.Negative) =>
        val d = math.sqrt((p.x - center.x)*(p.x - center.x) + (p.y - center.y)*(p.y - center.y))
        d should (be >= outerRadius and be <= (outerRadius + thickness))
    }
