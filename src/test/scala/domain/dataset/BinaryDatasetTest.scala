package domain.dataset

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import domain.data.{Label, LabeledPoint2D, Point2D}
import domain.pattern.SpiralCurve
import domain.sampling.{Domain, PointSampler}
import domain.util.Space

import scala.util.Random

class BinaryDatasetTest extends AnyFunSuite with Matchers:

  test("BinaryDataset should return a positive sample with Positive label"):

    val positiveSampler = new PointSampler:
      override def sample(): Point2D = Point2D(1.0, 1.0)

    val negativeSampler = new PointSampler:
      override def sample(): Point2D = Point2D(-1.0, -1.0)

    val dataset = new BinaryDataset(positiveSampler, negativeSampler)

    val sample = dataset.sample(Label.Positive)

    sample shouldBe LabeledPoint2D(Point2D(1.0, 1.0), Label.Positive)


  test("BinaryDataset should return a negative sample with Negative label"):

    val positiveSampler = new PointSampler:
      override def sample(): Point2D = Point2D(1.0, 1.0)

    val negativeSampler = new PointSampler:
      override def sample(): Point2D = Point2D(-1.0, -1.0)

    val dataset = new BinaryDataset(positiveSampler, negativeSampler)

    val sample = dataset.sample(Label.Negative)

    sample shouldBe LabeledPoint2D(Point2D(-1.0, -1.0), Label.Negative)

  given Space = Space(width = 100.0, height = 100.0)
  given Random = new Random(42L)
  