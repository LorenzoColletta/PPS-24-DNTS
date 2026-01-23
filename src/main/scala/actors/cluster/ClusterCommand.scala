package actors.cluster

import actors.gossip.GossipProtocol.GossipCommand
import akka.actor.typed.ActorRef
import akka.cluster.ClusterEvent.{MemberEvent, ReachabilityEvent}

/**
 * Defines the internal protocol of messages handled by cluster behaviors.
 */
sealed trait ClusterCommand


final case class NodesRefRequest(replyTo: ActorRef[Set[ActorRef[GossipCommand]]]) extends ClusterCommand

/**
 * Wraps an Akka Cluster [[MemberEvent]] for internal handling.
 *
 * @param e
 * The original cluster member event (e.g., [[akka.cluster.ClusterEvent.MemberUp]], [[akka.cluster.ClusterEvent.MemberRemoved]])
 */
final case class MemberCommand(e: MemberEvent) extends ClusterCommand

/**
 * Wraps an Akka Cluster [[ReachabilityEvent]] for internal handling.
 *
 * @param e
 * The original cluster reachability event (e.g., [[akka.cluster.ClusterEvent.ReachableMember]], [[akka.cluster.ClusterEvent.UnreachableMember]])
 */
final case class ReachabilityCommand(e: ReachabilityEvent) extends ClusterCommand

/**
 * Signals that the cluster simulation should start.
 */
case object StartSimulation extends ClusterCommand

/**
 * Signals that the cluster simulation should stop.
 */
case object StopSimulation extends ClusterCommand
