package actors.cluster

import akka.actor.Address
import akka.cluster.ClusterEvent.{MemberRemoved, MemberUp, ReachableMember, UnreachableMember}

/**
 * Defines the internal protocol of messages handled by cluster behavior.
 */
object ClusterProtocol:

  sealed trait ClusterMemberCommand

  sealed trait NodeEvent extends ClusterMemberCommand

  /**
   * Wraps an Akka Cluster [[MemberUp]] for internal handling.
   *
   * @param node
   * The original cluster member event.
   */
  final case class NodeUp(node: MemberUp) extends NodeEvent

  /**
   * Wraps an Akka Cluster [[MemberRemoved]] for internal handling.
   *
   * @param node
   * The original cluster member event.
   */
  final case class NodeRemoved(node: MemberRemoved) extends NodeEvent

  /**
   * Wraps an Akka Cluster [[ReachableMember]] for internal handling.
   *
   * @param node
   * The original cluster member event.
   */
  final case class NodeReachable(node: ReachableMember) extends NodeEvent

  /**
   * Wraps an Akka Cluster [[UnreachableMember]] for internal handling.
   *
   * @param node
   * The original cluster member event.
   */
  final case class NodeUnreachable(node: UnreachableMember) extends NodeEvent


  sealed trait AppClusterCommand extends ClusterMemberCommand

  /**
   * Signals that the cluster simulation should start.
   */
  case object StartSimulation extends AppClusterCommand

  /**
   * Signals that the cluster simulation should stop.
   */
  case object StopSimulation extends AppClusterCommand


  sealed trait InternalEvent extends ClusterMemberCommand

  /**
   * Signals the end of the time within which to successfully join the cluster.
   */
  case object JoinTimeout extends InternalEvent

  /**
   * Signals the end time from which a node is considered no longer recoverable.
   *
   * @param node the node no longer recoverable.
   */
  final case class UnreachableTimeout(node: Address) extends InternalEvent

  /**
   * Signals the end time from which the Seed is considered no longer recoverable.
   */
  case object SeedUnreachableTimeout extends InternalEvent
