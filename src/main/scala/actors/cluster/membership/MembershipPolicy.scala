package actors.cluster.membership

import actors.cluster.ClusterProtocol.*

/**
 * Defines the policy for reacting to cluster membership and reachability events.
 */
object MembershipPolicy:

  /**
   * Manages a state change according to the received event.
   *
   * @param membership
   * The current cluster membership state.
   * @param event
   * A cluster-related event derived from Akka Cluster events.
   * @return
   * The updated [[ClusterMembership]] after applying the change.
   */
  def update(membership: ClusterMembership, event: NodeEvent): ClusterMembership = event match

    case NodeUp(node) => membership.addNode(node.address)

    case NodeUnreachable(node) => membership.markUnreachable(node.address)

    case NodeReachable(node) => membership.markReachable(node.address)

    case NodeRemoved(node) => membership.removeNode(node.address)

