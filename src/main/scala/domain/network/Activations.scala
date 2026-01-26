package domain.network

/**
 * Interface defining a non-linear activation function used in neural layers.
 * It encapsulates the function logic, its derivative for backpropagation,
 * and initialization heuristics.
 */
trait Activation:

  /** @return The unique string identifier for this activation. */
  def name: String

  /** 
   * Applies the activation function. 
   * 
   * @param x scalar input.
   */
  def apply(x: Double): Double

  /** 
   * Computes the derivative of the activation function.
   *
   * @param x scalar input.
   */
  def derivative(x: Double): Double

  /**
   * Calculates the recommended standard deviation for weight initialization (e.g., Xavier or He).
   *
   * @param nIn  Number of input neurons.
   * @param nOut Number of output neurons.
   * @return The optimal sigma value for random weight generation.
   */
  def standardDeviation(nIn: Int, nOut: Int): Double


/**
 * Standard implementations of common activation functions.
 */
enum Activations extends Activation:
  case Sigmoid
  case Relu
  case LeakyRelu
  case Tanh

  override def name: String = this.toString

  override def apply(x: Double): Double =
    this match
      case Sigmoid   => 1.0 / (1.0 + Math.exp(-x))
      case Relu      => Math.max(0, x)
      case LeakyRelu => if x > 0 then x else 0.01 * x
      case Tanh      => Math.tanh(x)

  override def derivative(x: Double): Double =
    this match
      case Sigmoid =>
        val s = apply(x)
        s * (1.0 - s)
      case Relu =>
        if x > 0 then 1.0 else 0.0
      case LeakyRelu =>
        if x > 0 then 1.0 else 0.01
      case Tanh =>
        val t = apply(x)
        1.0 - (t * t)

  override def standardDeviation(nIn: Int, nOut: Int): Double =
    this match
      case Sigmoid | Tanh => Math.sqrt(1.0 / ((nIn + nOut) / 2.0))
      case Relu | LeakyRelu => Math.sqrt(2.0 / nIn)


/**
 * Registry for looking up activations by name.
 */
object Activations:

  /** A map of available activations indexed by their lowercase name. */
  given registry: Map[String, Activation] =
    Activations.values.map(a => a.name.toLowerCase -> a).toMap
