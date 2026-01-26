package domain.network

/**
 * Defines the available regularization strategies for preventing overfitting 
 * by adding a penalty term to the loss function.
 */
enum Regularization:
  /** No regularization applied. */
  case None

  /** L1 Regularization (Lasso). */
  case L1(rate: Double)

  /** L2 Regularization (Ridge). */
  case L2(rate: Double)

  /** Elastic Net Regularization. Linearly combines the L1 and L2 penalties. */
  case ElasticNet(l1Rate: Double, l2Rate: Double)


/** Companion object */
object Regularization:
  
  /**
   * Factory method to create a [[Regularization]] instance from a name.
   *
   * @param name The name of the regularization type.
   * @param paramGetter A function that retrieves a Double parameter by its name.
   * @return The configured [[Regularization]] instance.
   * @throws IllegalArgumentException if the name is unknown.
   */
  def fromName(name: String, paramGetter: String => Double): Regularization =
    name.toLowerCase match
      case "l1" =>
        L1(paramGetter("rate"))
      case "l2" =>
        L2(paramGetter("rate"))
      case "elasticnet" | "elastic_net" =>
        ElasticNet(paramGetter("l1_rate"), paramGetter("l2_rate"))
      case "none" =>
        None
      case _ =>
        throw new IllegalArgumentException(s"Unknown regularization type: $name")
