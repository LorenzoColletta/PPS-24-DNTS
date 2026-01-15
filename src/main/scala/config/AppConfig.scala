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

  /** The filename used for logging the node network. */
  def netLogFileName: String


/**
 * Default Production Configuration.
 */
object ProductionConfig extends AppConfig:
  override final val metricsInterval: FiniteDuration = 500.millis
  override final val batchInterval: FiniteDuration = 10.millis

  override final val netLogFileName: String =
    val now = LocalDateTime.now()
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
    val timestamp = now.format(formatter)
    s"node_network_$timestamp.log"
