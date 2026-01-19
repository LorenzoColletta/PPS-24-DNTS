package view

import domain.network.Model
import actors.trainer.TrainerActor.TrainingConfig

/**
 * Data recap for initial screen and live monitoring
 */
case class ViewStateSnapshot(
  epoch: Int = 0,
  model: Option[Model] = None,
  config: Option[TrainingConfig] = None,
  trainLoss: Option[Double] = None,
  testLoss: Option[Double] = None,
  consensus: Option[Double] = None,
  activePeers: Int = 1,
  totalPeers: Int = 1,
  clusterSeed: Option[String] = None,
)


/**
 * Boundary interface that defines how the application logic interacts with the User Interface.
 */
trait ViewBoundary:

  /**
   * Displays the initial setup screen after a successful cluster creation/connection.
   *
   * @param snapshot The current snapshot of the system state.
   * @param isMaster True if this node is the cluster master.
   */
  def showInitialScreen(snapshot: ViewStateSnapshot, isMaster: Boolean): Unit

  /**
   * Displays a fatal error message if the application bootstrap fails.
   *
   * @param reason The error description.
   */
  def showInitialError(reason: String): Unit

  /**
   * Switches the UI view to the active simulation screen.
   *
   * @param snapshot The state snapshot containing the data config to display.
   */
  def startSimulation(snapshot: ViewStateSnapshot): Unit

  /**
   * Updates the counter displaying connected nodes in the cluster.
   *
   * @param active The number of active peers.
   * @param total  The total expected peers.
   */
  def updatePeerDisplay(active: Int, total: Int): Unit

  /**
   * updates the charts for loss and consensus.
   *
   * @param epoch     The current training epoch.
   * @param trainLoss The training loss value.
   * @param testLoss  The validation loss value.
   * @param consensus The consensus metric (divergence from cluster average).
   */
  def plotMetrics(epoch: Int, trainLoss: Double, testLoss: Double, consensus: Double): Unit

  /**
   * Renders the visual representation of the neural network's decision boundary.
   *
   * @param model The current model.
   */
  def plotDecisionBoundary(model: Model): Unit

  /**
   * Toggles the UI state between Running and Paused.
   *
   * @param paused True if the simulation is currently paused.
   */
  def setPausedState(paused: Boolean): Unit

  /**
   * Displays an alert when a crash is simulated.
   */
  def showCrashMessage(): Unit

  /**
   * Clears the charts and resets the UI to a stopped state.
   */
  def stopSimulation(): Unit
