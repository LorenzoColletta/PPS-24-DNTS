package domain.network

trait Activation:
  def name: String
  def apply(x: Double): Double
  def derivative(x: Double): Double
  def standardDeviation(nIn: Int, nOut: Int): Double


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


object Activations:

  given registry: Map[String, Activation] =
    Activations.values.map(a => a.name.toLowerCase -> a).toMap

  def available: List[Activation] = Activations.values.toList
