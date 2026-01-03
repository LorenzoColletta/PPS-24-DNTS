package domain.network

trait Activation:
  def name: String
  def apply(x: Double): Double
  def derivative(x: Double): Double
  def standardDeviation(nIn: Int, nOut: Int): Double

object Activations:
  given sigmoid: Activation with
    def name = "Sigmoid"
    def apply(x: Double): Double = 1.0 / (1.0 + Math.exp(-x))
    def derivative(x: Double): Double =
      val s = apply(x)
      s * (1.0 - s)
    def standardDeviation(nIn: Int, nOut: Int): Double = Math.sqrt(1.0 / ((nIn + nOut) / 2))

  given relu: Activation with
    def name = "ReLU"
    def apply(x: Double): Double = Math.max(0, x)
    def derivative(x: Double): Double = if x > 0 then 1.0 else 0.0
    def standardDeviation(nIn: Int, nOut: Int): Double = Math.sqrt(2.0 / nIn)

  given leakyRelu: Activation with
    def name = "LeakyReLU"
    def apply(x: Double): Double = if x > 0 then x else 0.01 * x
    def derivative(x: Double): Double = if x > 0 then 1.0 else 0.01
    def standardDeviation(nIn: Int, nOut: Int): Double = Math.sqrt(2.0 / nIn)

  given tanh: Activation with
    def name = "Tanh"
    def apply(x: Double): Double = Math.tanh(x)
    def derivative(x: Double): Double =
      val t = apply(x)
      1.0 - (t * t)
    def standardDeviation(nIn: Int, nOut: Int): Double = Math.sqrt(1.0 / ((nIn + nOut) / 2))

