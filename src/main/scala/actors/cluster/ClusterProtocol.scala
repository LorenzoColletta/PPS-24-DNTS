package actors.cluster

import akka.cluster.ClusterEvent.{MemberEvent, ReachabilityEvent}

/**
 * Defines the internal protocol of messages handled by cluster behaviors.
 */
sealed trait ClusterProtocol

/**
 * Wraps an Akka Cluster [[MemberEvent]] for internal handling.
 *
 * @param e
 * The original cluster member event (e.g., [[akka.cluster.ClusterEvent.MemberUp]], [[akka.cluster.ClusterEvent.MemberRemoved]])
 */
final case class MemberProtocol(e: MemberEvent) extends ClusterProtocol

/**
 * Wraps an Akka Cluster [[ReachabilityEvent]] for internal handling.
 *
 * @param e
 * The original cluster reachability event (e.g., [[akka.cluster.ClusterEvent.ReachableMember]], [[akka.cluster.ClusterEvent.UnreachableMember]])
 */
final case class ReachabilityProtocol(e: ReachabilityEvent) extends ClusterProtocol

/**
 * Signals that the cluster simulation should start.
 */
case object StartSimulation extends ClusterProtocol

/**
 * Signals that the cluster simulation should stop.
 */
case object StopSimulation extends ClusterProtocol

/**
 * Signals the failure against joining the cluster  
 */
case object JoinTimeout extends ClusterProtocol

/**
 * Signals that the seed could be unreachable
 */
case object SeedUnreachable extends ClusterProtocol