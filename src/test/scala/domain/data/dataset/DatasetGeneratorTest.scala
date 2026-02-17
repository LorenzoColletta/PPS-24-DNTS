package domain.data.dataset

import domain.data.{Label, LabeledPoint2D, Point2D}
import domain.data.Label.*
import domain.data.dataset.{DatasetGenerator, LabeledDatasetModel, withNoise}
import domain.data.util.{Noise, Space}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class DatasetGeneratorTest extends AnyFunSuite with Matchers:

  private val fakeModel = new LabeledDatasetModel:
    override def sample(label: Label): LabeledPoint2D =
      label match
        case Positive => LabeledPoint2D(Point2D(1.0, 1.0), Positive)
        case Negative => LabeledPoint2D(Point2D(-1.0, -1.0), Negative)

  test("generate should create a dataset of the requested size"):
    val dataset = DatasetGenerator.generate(size = 10, model = fakeModel)

    dataset.size shouldBe 10


  test("generate should create half positive and half negative labels (rounded)"):
    val dataset = DatasetGenerator.generate(size = 5, model = fakeModel)

    val positives = dataset.count(_.label == Positive)
    val negatives = dataset.count(_.label == Negative)

    positives shouldBe 2
    negatives shouldBe 3


  test("generate should delegate sampling to the provided model"):
    val dataset = DatasetGenerator.generate(size = 4, model = fakeModel)

    dataset.collect { case LabeledPoint2D(p, Positive) => p } should
      contain only Point2D(1.0, 1.0)

    dataset.collect { case LabeledPoint2D(p, Negative) => p } should
      contain only Point2D(-1.0, -1.0)


  given Space = Space(100.0, 100.0)

  private val identityNoise = new Noise:
    override def apply(p: Point2D)(using Space): Point2D = p

  private val shiftNoise = new Noise:
    override def apply(p: Point2D)(using Space): Point2D =
      Point2D(p.x + 1.0, p.y - 1.0)

  test("withNoise should preserve dataset size and labels"):
    val dataset = List(
      LabeledPoint2D(Point2D(0.0, 0.0), Label.Positive),
      LabeledPoint2D(Point2D(1.0, 1.0), Label.Negative)
    )

    val noisy = dataset.withNoise(identityNoise)

    noisy.map(_.label) shouldBe dataset.map(_.label)
    noisy.size shouldBe dataset.size


  test("withNoise should apply noise to all points"):
    val dataset = List(
      LabeledPoint2D(Point2D(0.0, 0.0), Label.Positive),
      LabeledPoint2D(Point2D(2.0, 2.0), Label.Negative)
    )

    val noisy = dataset.withNoise(shiftNoise)

    noisy.map(_.point) shouldBe List(
      Point2D(1.0, -1.0),
      Point2D(3.0, 1.0)
    )
