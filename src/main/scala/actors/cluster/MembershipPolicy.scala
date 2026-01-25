package actors.cluster

import actors.gossip.GossipActor.GossipCommand
import akka.cluster.ClusterEvent.*
import akka.actor.typed.ActorRef
import actors.monitor.MonitorActor.MonitorCommand
import actors.monitor.MonitorActor.MonitorCommand.PeerCountChanged

/**
 * Defines the policy for reacting to cluster membership and reachability events.
 */
object MembershipPolicy:

  /**
   * Applies a cluster command to the current membership state.
   *
   * @param membership
   * The current cluster membership state.
   * @param command
   * A cluster-related command derived from Akka Cluster events.
   * @param monitor
   * Actor responsible for observing cluster metrics (e.g. peer counts).
   * @param gossip
   * Actor responsible for propagating cluster membership information
   * to other nodes.
   * @return
   * The updated [[ClusterMembership]] after applying the command.
   */
  def apply(
    membership: ClusterMembership,
    command: ClusterCommand,
    monitor: ActorRef[MonitorCommand],
    gossip: ActorRef[GossipCommand]
  ): ClusterMembership =
    command match
      case MemberCommand(MemberUp(m)) =>
        val updated = membership.addPeer(m.address)
        monitor ! PeerCountChanged(updated.available, updated.total)
        updated

      case MemberCommand(MemberRemoved(m, _)) =>
        val updated = membership.removePeer(m.address)
//        gossip ! GossipCommand.PeerDown(m.address)
        monitor ! PeerCountChanged(updated.available, updated.total)
        updated

      case ReachabilityCommand(UnreachableMember(m)) =>
        val updated = membership.markUnreachable(m.address)
//        gossip ! GossipCommand.PeerUnreachable(m.address)
        monitor ! PeerCountChanged(updated.available, updated.total)
        updated

      case ReachabilityCommand(ReachableMember(m)) =>
        val updated = membership.markReachable(m.address)
//        gossip ! GossipCommand.PeerReachable(m.address)
        monitor ! PeerCountChanged(updated.available, updated.total)
        updated

      case _ =>
        membership
