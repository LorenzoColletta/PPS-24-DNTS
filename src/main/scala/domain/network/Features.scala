package domain.network

import domain.data.Point2D
import domain.data.LinearAlgebra.Vector

enum Feature:
  case X
  case Y
  case SquareX
  case SquareY
  case ProductXY
  case SinX
  case SinY

object FeatureTransformer:
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
