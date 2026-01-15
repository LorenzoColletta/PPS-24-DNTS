package config

import scala.concurrent.duration.*

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
  override final val netLogFileName: String = "node_network.log"
