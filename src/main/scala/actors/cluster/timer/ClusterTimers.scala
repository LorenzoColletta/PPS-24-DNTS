package actors.cluster.timer

import com.typesafe.config.Config

import scala.concurrent.duration.*

/**
 * Timers duration configuration. 
 */
final case class ClusterTimers(
  bootstrapCheck: FiniteDuration,
  unreachableNode: FiniteDuration
)

object ClusterTimers:

  /**
   * Loads the duration of the used timers from application.conf.
   *
   * Expected path:
   *
   * cluster.timers {
   *   bootstrap-check = 10s
   *   unreachable-node = 30s
   * }
   */
  def fromConfig(config: Config): ClusterTimers =
    val timersConfig = config.getConfig("cluster.timers")

    ClusterTimers(
      bootstrapCheck = timersConfig.getDuration("bootstrap-check").toMillis.millis,
      unreachableNode = timersConfig.getDuration("unreachable-node").toMillis.millis
    )
