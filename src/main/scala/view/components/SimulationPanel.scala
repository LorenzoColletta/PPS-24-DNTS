package view.components

import javax.swing.*
import javax.swing.border.EmptyBorder
import java.awt.{BorderLayout, Color, FlowLayout, Font, GridLayout}

import config.AppConfig
import domain.network.Model
import actors.monitor.MonitorActor.MonitorCommand
import view.ViewStateSnapshot

/**
 * Contains constants and static definitions for the [[SimulationPanel]].
 */
object SimulationPanel:
  private object Dimension:
    final val resWBoundary = 50
    final val resHBoundary = resWBoundary

  private object Text:
    final val StatusReady = "Status: READY"
    final val StatusRunning = "Status: RUNNING"
    final val StatusPaused = "Status: PAUSED"
    final val StatusStopped = "Status: STOPPED"
    final val StatusCrashed = "Status: CRASHED"
    final val StatusFinished = "Status: FINISHED"

    final val BtnPause = "PAUSE"
    final val BtnResume = "RESUME"
    final val BtnStop = "STOP"
    final val BtnExport = "EXPORT WEIGHTS"
    final val BtnCrash = "CRASH NODE"

    final val TooltipStop = "Request STOP for global simulation and application exit"
    final val TooltipCrash = "Simulates a critical failure and terminates the node"
    final val TooltipExport = "Saves the current node model weights to a JSON file"

  private object Style:
    final val InfoFont = new Font("Monospaced", Font.BOLD, 14)
    final val StatusFont = new Font("SansSerif", Font.BOLD, 12)


/**
 * A Swing panel designed to display the main visualization dashboard for the active simulation.
 * It displays real-time training metrics (loss, consensus), renders the decision boundary
 * of the neural network, and provides controls to manage the simulation lifecycle.
 *
 * @param config Implicit application configuration.
 */
class SimulationPanel(using config: AppConfig) extends JPanel:
  import SimulationPanel.*

  private var controllerCallback: MonitorCommand => Unit = _ => ()

  private val lblEpoch = new JLabel("Epoch: 0")
  private val lblPeers = new JLabel("Peers: 0/0")

  private val metricsPanel = new MetricPlotter()
  private val boundaryPanel = new BoundaryPlotter(
    Dimension.resWBoundary, Dimension.resHBoundary
  )(using config.space)

  private val lblStatus = new JLabel(Text.StatusReady)
  private val btnPause = new JButton(Text.BtnPause)
  private val btnStop = new JButton(Text.BtnStop)
  private val btnExport = new JButton(Text.BtnExport)
  private val btnCrash = new JButton(Text.BtnCrash)


  initLayout()
  setupListeners()


  /**
   * Registers the callback function to handle user interactions from the control panel.
   *
   * @param cb A function that accepts a [[MonitorCommand]] to be sent to the system.
   */
  def setControllerCallback(cb: MonitorCommand => Unit): Unit =
    this.controllerCallback = cb

  /**
   * Prepares and resets the panel for a new simulation session.
   *
   * @param snapshot The initial state of the simulation containing configuration and starting model.
   */
  def initSimulation(snapshot: ViewStateSnapshot): Unit =
    SwingUtilities.invokeLater(() =>
      lblStatus.setText(Text.StatusRunning)
      lblStatus.setForeground(Color.BLACK)
      btnPause.setText(Text.BtnPause)
      setControlsEnabled(true)

      metricsPanel.clear()

      lblEpoch.setText(s"Epoch: ${snapshot.epoch}")
      updatePeerCount(snapshot.activePeers, snapshot.totalPeers)

      val train = snapshot.config.map(_.trainSet).getOrElse(Nil)
      val test = snapshot.config.map(_.testSet).getOrElse(Nil)
      boundaryPanel.initDataset(train, test)
      snapshot.model.foreach(m => boundaryPanel.renderModel(m))
    )

  /**
   * Updates the training metric plots with new data points.
   *
   * @param epoch The current training epoch.
   * @param train The current training loss.
   * @param test  The current test loss.
   * @param cons  The current consensus metric.
   */
  def updateMetrics(epoch: Int, train: Double, test: Double, cons: Double): Unit =
    metricsPanel.update(epoch, train, test, cons)
    SwingUtilities.invokeLater(() => lblEpoch.setText(s"Epoch: $epoch"))

  /**
   * Updates the label displaying the number of active peers.
   *
   * @param active The number of currently reachable peers.
   * @param total  The total number of known peers.
   */
  def updatePeerCount(active: Int, total: Int): Unit =
    SwingUtilities.invokeLater(() => lblPeers.setText(s"Active Peers: $active / $total"))

  /**
   * Refreshes the decision boundary visualization based on the latest model state.
   *
   * @param model The current neural network model to render.
   */
  def updateBoundary(model: Model): Unit =
    boundaryPanel.renderModel(model)

  /**
   * Updates UI and controls to reflect the paused or running state.
   *
   * @param paused True if the simulation is currently paused, false if running.
   */
  def setPaused(paused: Boolean): Unit =
    SwingUtilities.invokeLater(() =>
      btnPause.setText(if paused then Text.BtnResume else Text.BtnPause)
      lblStatus.setText(if paused then Text.StatusPaused else Text.StatusRunning)
    )

  /**
   * Updates the UI to indicate the simulation has stopped.
   */
  def stopSimulation(): Unit =
    lblStatus.setText(Text.StatusStopped)

  /**
   * Updates the UI to indicate a critical node crash.
   * Disables all control buttons and displays a warning dialog.
   */
  def showCrashState(): Unit =
    SwingUtilities.invokeLater(() =>
      lblStatus.setText(Text.StatusCrashed)
      lblStatus.setForeground(Color.RED)
      setControlsEnabled(false)
      JOptionPane.showMessageDialog(this, "System CRASHED as requested.", "CRASHED", JOptionPane.WARNING_MESSAGE)
    )

  /**
   * Updates the UI to indicate that the training session has completed successfully.
   * Disable pause/resume control button.
   */
  def simulationFinished(): Unit =
    SwingUtilities.invokeLater(() =>
      lblStatus.setText(Text.StatusFinished)
      lblStatus.setForeground(Color.GREEN)
      btnPause.setEnabled(false)
    )


  private def initLayout(): Unit =
    setLayout(new BorderLayout())

    val infoPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 30, 10))
    infoPanel.setBorder(new EmptyBorder(5, 0, 5, 0))

    lblEpoch.setFont(Style.InfoFont)
    lblPeers.setFont(Style.InfoFont)

    infoPanel.add(lblEpoch)
    infoPanel.add(new JSeparator(SwingConstants.VERTICAL))
    infoPanel.add(lblPeers)

    
    val plotsPanel = new JPanel(new GridLayout(1, 2, 10, 0))
    plotsPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10))
    plotsPanel.add(boundaryPanel)
    plotsPanel.add(metricsPanel)


    val controlsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10))
    
    btnCrash.setBackground(Color.RED)
    btnCrash.setForeground(Color.WHITE)
    lblStatus.setFont(Style.StatusFont)

    controlsPanel.add(lblStatus)
    controlsPanel.add(Box.createHorizontalStrut(20))
    controlsPanel.add(btnPause)
    btnStop.setToolTipText(Text.TooltipStop); controlsPanel.add(btnStop)
    controlsPanel.add(Box.createHorizontalStrut(20))
    btnExport.setToolTipText(Text.TooltipExport); controlsPanel.add(btnExport)
    btnCrash.setToolTipText(Text.TooltipCrash); controlsPanel.add(btnCrash)


    add(infoPanel, BorderLayout.NORTH)
    add(plotsPanel, BorderLayout.CENTER)
    add(controlsPanel, BorderLayout.SOUTH)

  private def setupListeners(): Unit =
    btnStop.addActionListener(_ => controllerCallback(MonitorCommand.StopSimulation))

    btnPause.addActionListener(_ =>
      val cmd = if btnPause.getText == Text.BtnPause
      then MonitorCommand.PauseSimulation
      else MonitorCommand.ResumeSimulation
      controllerCallback(cmd)
    )

    btnExport.addActionListener(_ => controllerCallback(MonitorCommand.RequestWeightsLog))

    btnCrash.addActionListener(_ =>
      if JOptionPane.showConfirmDialog(
        this, "Simulate Node CRASH?", "Confirm", JOptionPane.YES_NO_OPTION
      ) == JOptionPane.YES_OPTION then controllerCallback(MonitorCommand.SimulateCrash)
    )

  private def setControlsEnabled(enabled: Boolean): Unit =
    btnPause.setEnabled(enabled)
    btnStop.setEnabled(enabled)
    btnExport.setEnabled(enabled)
    btnCrash.setEnabled(enabled)
