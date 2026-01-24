package actors.cluster

import actors.gossip.GossipActor.GossipCommand
import actors.monitor.MonitorActor.MonitorCommand
import akka.actor.typed.*
import akka.actor.typed.scaladsl.*
import akka.cluster.ClusterEvent.*
import akka.cluster.typed.{Cluster, Leave}

/**
 * Defines the core cluster behaviors for nodes in the system.
 *
 * [[ClusterBehaviors]] provides two main states for a cluster node:
 *
 * 1. **Connecting** – the node is establishing membership in the cluster.
 *    - Accepts membership and reachability events.
 *    - Waits for the `StartSimulation` command to transition to `running`.
 *    - In the connecting state, all new peers can join freely.
 *
 * 2. **Running** – the node is actively participating in the simulation/task.
 *    - Still processes membership and reachability events via [[MembershipPolicy]].
 *    - New peers attempting to join while running are ignored or rejected.
 *    - Waits for the `StopSimulation` command to return to `connecting`.
 */
object ClusterBehaviors:

  /**
   * Behavior representing the node in the "connecting" state.
   *
   * @param context    the actor context for the current node
   * @param membership the current cluster membership
   * @param monitor    actor for monitoring peer count changes
   * @param gossip     actor for gossiping membership changes
   * @return a behavior representing the connecting state
   */
  def connecting(
    context: ActorContext[ClusterCommand],
    membership: ClusterMembership,
    monitor: ActorRef[MonitorCommand],
    gossip: ActorRef[GossipCommand]
  ): Behavior[ClusterCommand] =

    Behaviors.receiveMessage {

      case StartSimulation =>
        running(context, membership, monitor, gossip)

      case StopSimulation =>
        Behaviors.same

      case cmd =>
        val updated =
          MembershipPolicy(membership, cmd, monitor, gossip)
        connecting(context, updated, monitor, gossip)
    }

  /**
   * Behavior representing the node in the "running" state.
   *
   * @param context    the actor context for the current node
   * @param membership the current cluster membership
   * @param monitor    actor for monitoring peer count changes
   * @param gossip     actor for gossiping membership changes
   * @return a behavior representing the running state
   */
  private def running(
    context: ActorContext[ClusterCommand],
    membership: ClusterMembership,
    monitor: ActorRef[MonitorCommand],
    gossip: ActorRef[GossipCommand]
  ): Behavior[ClusterCommand] =

    Behaviors.receiveMessage {

      case MemberCommand(MemberUp(m)) =>
        val cluster = Cluster(context.system)

        if (cluster.state.leader.contains(cluster.selfMember.address)) {
          cluster.manager ! Leave(m.address)
        }
        Behaviors.same

      case StopSimulation =>
        connecting(context, membership, monitor, gossip)

      case StartSimulation =>
        Behaviors.same

      case cmd =>
        val updated =
          MembershipPolicy(membership, cmd, monitor, gossip)
        running(context, updated, monitor, gossip)
    }

