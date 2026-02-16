package view.components

import org.jfree.chart.{ChartFactory, ChartPanel, JFreeChart}
import org.jfree.chart.plot.PlotOrientation
import org.jfree.data.xy.{XYSeries, XYSeriesCollection}
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer
import javax.swing.{JPanel, SwingUtilities}
import java.awt.{BasicStroke, BorderLayout, Color}

/**
 * A Swing panel designed to display real-time training metrics.
 * It plots Training Loss, Test Loss, and the Consensus Metric over epochs.
 */
class MetricPlotter extends JPanel:

  private val trainLossSeries = new XYSeries("Train Loss")
  private val testLossSeries = new XYSeries("Test Loss")
  private val consensusSeries = new XYSeries("Consensus Loss")

  private val dataset = new XYSeriesCollection()
  dataset.addSeries(trainLossSeries)
  dataset.addSeries(testLossSeries)
  dataset.addSeries(consensusSeries)


  initLayout()


  /**
   * Adds a new data point to the charts.
   *
   * @param epoch     The x-axis value representing the current training iteration.
   * @param trainLoss The value for the Training Loss series.
   * @param testLoss  The value for the Test Loss series.
   * @param consensus The value for the Consensus Metric series.
   */
  def update(epoch: Int, trainLoss: Double, testLoss: Double, consensus: Double): Unit =
    if !SwingUtilities.isEventDispatchThread then
      SwingUtilities.invokeLater(() => update(epoch, trainLoss, testLoss, consensus))
    else
      trainLossSeries.setNotify(false)
      testLossSeries.setNotify(false)
      consensusSeries.setNotify(false)
      try
        trainLossSeries.add(epoch, trainLoss)
        testLossSeries.add(epoch, testLoss)
        consensusSeries.add(epoch, consensus)
      finally
        trainLossSeries.setNotify(true)
        testLossSeries.setNotify(true)
        consensusSeries.setNotify(true)

  /**
   * Clears all data series, resetting the chart to its initial empty state.
   */
  def clear(): Unit =
    SwingUtilities.invokeLater(() =>
      trainLossSeries.clear()
      testLossSeries.clear()
      consensusSeries.clear()
    )


  private def initLayout(): Unit =
    setLayout(new BorderLayout())

    val chart = createChart()
    val chartPanel = new ChartPanel(chart)

    add(chartPanel, BorderLayout.CENTER)

  private def createChart(): JFreeChart =
    val chart = ChartFactory.createXYLineChart(
      "Training Metrics",
      "Epoch", "Value", dataset,
      PlotOrientation.VERTICAL, true, true, false
    )

    val plot = chart.getXYPlot
    val renderer = new XYLineAndShapeRenderer(true, false)

    renderer.setSeriesPaint(0, Color.RED);   renderer.setSeriesStroke(0, new BasicStroke(2.0f))
    renderer.setSeriesPaint(1, Color.BLUE);  renderer.setSeriesStroke(1, new BasicStroke(2.0f))
    renderer.setSeriesPaint(2, Color.GREEN); renderer.setSeriesStroke(2, new BasicStroke(2.0f))

    plot.setRenderer(renderer)
    plot.setBackgroundPaint(Color.WHITE)
    plot.setRangeGridlinePaint(Color.LIGHT_GRAY)
    chart
