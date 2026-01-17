package config

import scala.concurrent.duration.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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

/**
 * Provides the implicit configuration instance for the actors.
 */
object ConfigModule:
  given AppConfig = ProductionConfig