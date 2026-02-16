package config

import scala.concurrent.duration.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import domain.util.Space
import domain.training.LossFunction
import domain.training.Strategies.Losses

/**
 * Defines the configuration contract for the system.
 */
trait AppConfig:
  /** The time interval at which the Monitor queries the ModelActor for metrics. */
  def metricsInterval: FiniteDuration

  /** The delay between processing two consecutive training batches. */
  def batchInterval: FiniteDuration

  /** The time interval at which the GossipActor triggers a synchronization with a peer. */
  def gossipInterval: FiniteDuration

  /** The filename used for logging the node network weights. */
  def netLogFileName: String
  
  /** Defines the boundaries of the 2D plane used for data generation and visualization. */
  def space: Space

  /** The loss function used to measure the network performance. */
  def lossFunction: LossFunction


/**
 * Default Production Configuration.
 */
object ProductionConfig extends AppConfig:
  /** UI and metrics refresh rate. */
  override final val metricsInterval: FiniteDuration = 500.millis

  /** Local training speed (delay between batches). */
  override final val batchInterval: FiniteDuration = 10.millis

  /** P2P synchronization frequency. */
  override final val gossipInterval: FiniteDuration = 2.seconds

  /** Dynamic log filename generation with timestamp. */
  override final val netLogFileName: String =
    val now = LocalDateTime.now()
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
    val timestamp = now.format(formatter)
    s"node_network_$timestamp.log"

  /** Defines a 100x100 coordinate space. */
  override final val space: Space = Space(50.0, 50.0)

  /** Define Mean Squared Error (MSE) as the standard loss metric. */
  override final val lossFunction: LossFunction = Losses.mse
