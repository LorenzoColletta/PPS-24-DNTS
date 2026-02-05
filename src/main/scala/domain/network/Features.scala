package domain.network

import domain.data.Point2D
import domain.data.LinearAlgebra.Vector

/**
 * Enumerates the available feature engineering transformations.
 * These are used to project raw 2D coordinates into a higher-dimensional feature space.
 */
enum Feature:
  case X
  case Y
  case SquareX
  case SquareY
  case ProductXY
  case SinX
  case SinY


/**
 * Utility responsible for converting domain objects into Linear Algebra [[Vector]].
 */
object FeatureTransformer:
  /**
   * Transforms a geometric [[Point2D]] into a Linear Algebra [[Vector]] based on the selected features.
   *
   * @param point    The raw input 2D point (x, y).
   * @param features The list of active feature transformations to apply.
   * @return A [[Vector]] containing the transformed values in the order of the feature list.
   */
  def transform(point: Point2D, features: List[Feature]): Vector = {
    val values = features.map {
      case Feature.X         => point.x
      case Feature.Y         => point.y
      case Feature.SquareX   => math.pow(point.x, 2)
      case Feature.SquareY   => math.pow(point.y, 2)
      case Feature.ProductXY => point.x * point.y
      case Feature.SinX      => math.sin(point.x)
      case Feature.SinY      => math.sin(point.y)
    }
    Vector.fromList(values)
  }
