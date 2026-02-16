package view.components

import domain.data.util.Space
import java.awt.{BasicStroke, Color, Graphics, Graphics2D, RenderingHints}
import java.awt.image.BufferedImage
import javax.swing.{BorderFactory, JPanel, SwingUtilities}
import scala.concurrent.{Future, ExecutionContext}
import scala.util.{Success, Failure}

import domain.data.{Label, LabeledPoint2D, Point2D}
import domain.network.Model

/**
 * Contains constants and static definitions for the [[BoundaryPlotter]].
 */
object BoundaryPlotter:
  private object RenderConfig:
    final val ScaleFactor = 4
    final val HeatmapAlphaFactor = 0.8f

  private object Dimension:
    final val PointSizeTrain = 6
    final val PointSizeTest = 8

  private object Style:
    final val StrokeTrain = new BasicStroke(1.0f)
    final val StrokeTest = new BasicStroke(1.0f)

  private object Colors:
    final val PosTrain = new Color(255, 0, 0, 255)
    final val PosTest  = new Color(255, 0, 0, 50)
    final val NegTrain = new Color(0, 0, 255, 255)
    final val NegTest  = new Color(0, 0, 255, 50)
    final val Border   = Color.BLACK


/**
 * A Swing panel designed to display the classification results on a 2D plane.
 * It draws a background heatmap representing the model's prediction
 * for every point in the space, overlaying the actual dataset points.
 *
 * @param resolutionW   The logical width of the plotting area.
 * @param resolutionH   The logical height of the plotting area.
 * @param space         Implicit definition of the 2D coordinate space boundaries.
 */
class BoundaryPlotter(
  resolutionW: Int,
  resolutionH: Int
)(using space: Space) extends JPanel:
  import BoundaryPlotter.*
  import scala.concurrent.ExecutionContext.Implicits.global

  private val gridW = resolutionW / RenderConfig.ScaleFactor
  private val gridH = resolutionH / RenderConfig.ScaleFactor

  private var trainPoints: List[LabeledPoint2D] = Nil
  private var testPoints: List[LabeledPoint2D] = Nil

  @volatile private var lastHeatmap: Option[BufferedImage] = None


  setBorder(BorderFactory.createLineBorder(Color.BLACK))


  /**
   * Loads the dataset into the plotter.
   *
   * @param train The list of training data points.
   * @param test  The list of testing data points.
   */
  def initDataset(train: List[LabeledPoint2D], test: List[LabeledPoint2D]): Unit =
    SwingUtilities.invokeLater(() =>
      this.trainPoints = train
      this.testPoints = test
      this.repaint()
    )

  /**
   * Triggers the asynchronous generation of the decision boundary heatmap.
   * Once calculation is complete, the UI is updated with the new background.
   *
   * @param model The current state of the neural network model used for prediction.
   */
  def renderModel(model: Model): Unit =
    Future {
      generateHeatmap(model)
    }.onComplete {
      case Success(newImage) =>
        SwingUtilities.invokeLater(() =>
          this.lastHeatmap = Some(newImage)
          this.repaint()
        )
      case Failure(ex) =>
        System.err.println(s"BoundaryPlotter Error: ${ex.getMessage}")
    }

  override def paintComponent(g: Graphics): Unit =
    super.paintComponent(g)
    val g2d = g.asInstanceOf[Graphics2D]

    val currentW = getWidth
    val currentH = getHeight

    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)

    lastHeatmap.foreach(img => g2d.drawImage(img, 0, 0, currentW, currentH, null))

    trainPoints.foreach(p => drawPoint(g2d, p, currentW, currentH, isTest = false))
    testPoints.foreach(p => drawPoint(g2d, p, currentW, currentH, isTest = true))

    g2d.setStroke(new BasicStroke(1.0f))


  private def drawPoint(g2d: Graphics2D, p: LabeledPoint2D, w: Int, h: Int, isTest: Boolean): Unit =
    val (sx, sy) = toScreen(p.point, w, h)
    val size = if isTest then Dimension.PointSizeTest else Dimension.PointSizeTrain

    val fillColor = p.label match
      case Label.Positive => if isTest then Colors.PosTest else Colors.PosTrain
      case Label.Negative => if isTest then Colors.NegTest else Colors.NegTrain

    g2d.setColor(fillColor)
    g2d.fillOval(sx - size / 2, sy - size / 2, size, size)

    g2d.setColor(Colors.Border)
    g2d.setStroke(if isTest then Style.StrokeTest else Style.StrokeTrain)
    g2d.drawOval(sx - size / 2, sy - size / 2, size, size)

  private def generateHeatmap(model: Model): BufferedImage =
    val img = new BufferedImage(gridW, gridH, BufferedImage.TYPE_INT_ARGB)

    for x <- 0 until gridW do
      for y <- 0 until gridH do
        val domainX = (x.toDouble / gridW) * space.width - space.width / 2
        val domainY = -((y.toDouble / gridH) * space.height - space.height / 2)

        val prediction = model.predict(Point2D(domainX, domainY))

        val color = if prediction > 0.5 then
          new Color(1f, 0f, 0f, ((prediction - 0.5) * RenderConfig.HeatmapAlphaFactor).toFloat)
        else
          new Color(0f, 0f, 1f, ((0.5 - prediction) * RenderConfig.HeatmapAlphaFactor).toFloat)

        img.setRGB(x, y, color.getRGB)
    img

  private def toScreen(p: Point2D, w: Int, h: Int): (Int, Int) =
    val sx = ((p.x + space.width / 2) / space.width * w).toInt
    val sy = ((-p.y + space.height / 2) / space.height * h).toInt
    (sx, sy)
