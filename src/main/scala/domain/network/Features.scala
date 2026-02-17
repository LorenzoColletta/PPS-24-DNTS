package domain.network

import domain.data.Point2D
import domain.data.LinearAlgebra.Vector
import domain.data.util.Space

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
   * @param space    The implicit definition of the boundaries of the 2D plane used.
   * @return A [[Vector]] containing the transformed values in the order of the feature list.
   */
  def transform(point: Point2D, features: List[Feature])(using space: Space): Vector = {
    val nx = point.x / (space.width / 2.0)
    val ny = point.y / (space.height / 2.0)

    val values = features.map {
      case Feature.X         => nx
      case Feature.Y         => ny
      case Feature.SquareX   => math.pow(nx, 2)
      case Feature.SquareY   => math.pow(ny, 2)
      case Feature.ProductXY => nx * ny
      case Feature.SinX      => math.sin(nx)
      case Feature.SinY      => math.sin(ny)
    }
    Vector.fromList(values)
  }
