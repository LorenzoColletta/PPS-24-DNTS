package view

import javax.swing.*
import java.awt.CardLayout
import java.awt.event.{WindowAdapter, WindowEvent}

import config.AppConfig
import domain.network.Model
import actors.monitor.MonitorActor.MonitorCommand
import view.components.{SetupInfoPanel, SimulationPanel}

/**
 * Contains constants and static definitions for the GUI.
 */
object GuiView:
  private object Dimensions:
    final val Width = 1000
    final val Height = 700

  private object Cards:
    final val Setup = "SETUP"
    final val Simulation = "SIMULATION"


/**
 * Concrete implementation of the [[ViewBoundary]] trait using the Java Swing library.
 * It manages the main application window and orchestrates the transition
 * between the initial setup screen and the active simulation dashboard.
 *
 * @param config Implicit application configuration.
 */
class GuiView(using config: AppConfig) extends ViewBoundary:
  import GuiView.*

  private val frame = new JFrame("DNTS - Distributed Neural Training")
  private val mainLayout = new CardLayout()
  private val mainPanel = new JPanel(mainLayout)

  private val setupPanel = new SetupInfoPanel()
  private val simulationPanel = new SimulationPanel()

  private var globalHandler: MonitorCommand => Unit = _ => ()


  initFrame()


  override def bindController(handler: MonitorCommand => Unit): Unit =
    this.globalHandler = handler
    simulationPanel.setControllerCallback(handler)

  override def showInitialScreen(snapshot: ViewStateSnapshot, isMaster: Boolean): Unit =
    setupPanel.render(snapshot, isMaster, () => globalHandler(MonitorCommand.StartSimulation))

    SwingUtilities.invokeLater(() =>
      mainLayout.show(mainPanel, Cards.Setup)
    )

  override def startSimulation(snapshot: ViewStateSnapshot): Unit =
    simulationPanel.initSimulation(snapshot)

    SwingUtilities.invokeLater(() =>
      mainLayout.show(mainPanel, Cards.Simulation)
    )

  override def stopSimulation(): Unit =
    SwingUtilities.invokeLater(() => {
      simulationPanel.stopSimulation()
      frame.dispose()
    })

  override def updatePeerDisplay(active: Int, total: Int): Unit =
    setupPanel.updatePeerCount(active, total)
    simulationPanel.updatePeerCount(active, total)

  override def plotDecisionBoundary(model: Model): Unit =
    simulationPanel.updateBoundary(model)

  override def plotMetrics(epoch: Int, trainLoss: Double, testLoss: Double, consensus: Double): Unit =
    simulationPanel.updateMetrics(epoch, trainLoss, testLoss, consensus)

  override def setPausedState(paused: Boolean): Unit =
    simulationPanel.setPaused(paused)

  override def showCrashMessage(): Unit =
    simulationPanel.showCrashState()

  override def simulationFinished(): Unit =
    simulationPanel.simulationFinished()

  override def showInitialError(reason: String): Unit =
    SwingUtilities.invokeLater(() =>
      JOptionPane.showMessageDialog(frame, reason, "Bootstrap Error", JOptionPane.ERROR_MESSAGE)
    )

  private def initFrame(): Unit =
    frame.setSize(Dimensions.Width, Dimensions.Height)
    frame.setLocationRelativeTo(null)

    frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE)
    frame.addWindowListener(new WindowAdapter {
      override def windowClosing(e: WindowEvent): Unit =
        if globalHandler != null then
          globalHandler(MonitorCommand.StopSimulation)
        else
          frame.dispose()
          System.exit(0)
    })

    mainPanel.add(setupPanel, Cards.Setup)
    mainPanel.add(simulationPanel, Cards.Simulation)

    frame.add(mainPanel)
    frame.setVisible(true)
