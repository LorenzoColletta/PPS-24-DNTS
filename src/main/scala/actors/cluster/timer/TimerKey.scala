package actors.cluster.timer

import akka.actor.Address


/**
 * Identifies a timer.
 */
sealed trait TimerKey

/**
 * Identifies a timer within which to verify the cluster connection success.
 */
case object BootstrapTimerId extends TimerKey

/**
 * Identifies a timer within which a node can become reachable again.
 * @param node the unreachable node
 */
final case class UnreachableTimerId(node: Address) extends TimerKey

