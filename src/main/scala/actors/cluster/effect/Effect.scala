package actors.cluster.effect

import actors.cluster.Phase
import actors.cluster.ClusterProtocol.InternalEvent
import actors.cluster.timer.TimerKey
import actors.discovery.DiscoveryProtocol.DiscoveryCommand
import actors.root.RootProtocol.RootCommand
import akka.actor.Address


/**
 * Effects produced by the ClusterManager's policies.
 */
sealed trait Effect

/**
 * Action derived from a cluster event.
 */
sealed trait Action extends Effect

/**
 * Requests marking a node as down.
 * @param node the node to be marked as down.
 */
final case class DownNode(node: Address) extends Action

/**
 * Requests the removal of a node from the cluster.
 * @param node the node to be removed.
 */
final case class RemoveNodeFromCluster(node: Address) extends Action

/**
 * Signals that the current node must leave the cluster.
 */
case object LeaveCluster extends Action

/**
 * Requests to send an event to the RootActor.
 * @param event the event to be sent to the RootActor.
 */
final case class NotifyRoot(event: RootCommand) extends Action

/**
 * Requests to send an event to the MonitorActor.
 */
case object NotifyMonitor extends Action

/**
 * Requests to send an event to the ReceptionistActor.
 */
final case class NotifyReceptionist(event: DiscoveryCommand) extends Action

/**
 * Starts a timer associated to an internal event.
 *
 * @param id the timer id.
 * @param event the internal event.
 */
final case class StartTimer(id: TimerKey, event: InternalEvent) extends Action

/**
 * Cancels a timer given its id.
 *
 * @param id    the timer id.
 */
final case class CancelTimer(id: TimerKey) extends Action

/**
 * Requests the stop of the cluster manager's behavior.
 */
case object StopBehavior extends Action

/**
 * Current cluster state view transition.
 */
sealed trait StateTransition extends Effect

/**
 * Requests the change of the application phase.
 * @param phase the phase to be set.
 */
final case class ChangePhase(phase: Phase) extends StateTransition

/**
 * Requests to remove a node from the current known nodes.
 * @param node the node to be removed.
 */
final case class RemoveNodeFromMembership(node: Address) extends StateTransition
