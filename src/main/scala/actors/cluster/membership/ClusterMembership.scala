package actors.cluster.membership

import akka.actor.Address

/**
 * Represents the local view of the cluster membership from the perspective
 * of this node.
 *
 * @param nodesUp
 * The set of all nodes that are currently part of the cluster,
 * including reachable and unreachable ones.
 * @param nodesUnreachable
 * The subset of [[nodesUp]] that are currently considered unreachable.
 */
final case class ClusterMembership(
                                    master: Option[Address],
                                    nodesUp: Set[Address],
                                    nodesUnreachable: Set[Address],
                                  ):

  /**
   * Sets the master Node.
   *
   * @return
   * A new [[ClusterMembership]] including the master node
   */
  def setMaster(address: Address): ClusterMembership =
    copy(master = Some(address))

  /**
   * Adds a new node to the cluster membership. Adding an already known node
   * does not change the resulting membership.
   *
   * @param address
   * The address of the node that has joined the cluster.
   * @return
   * A new [[ClusterMembership]] including the given node.
   */
  def addNode(address: Address): ClusterMembership =
    copy(nodesUp = nodesUp + address)

  /**
   * Removes a node from the cluster membership.
   *
   * @param address
   * The address of the node that has left or has been removed from the cluster.
   * @return
   * A new [[ClusterMembership]] without the given node.
   */
  def removeNode(address: Address): ClusterMembership =
    copy(
      nodesUp = nodesUp - address,
      nodesUnreachable = nodesUnreachable - address,
    )

  /**
   * Marks a node as unreachable.
   *
   * @param address
   * The address of the node that has become unreachable.
   * @return
   * A new [[ClusterMembership]] with the node marked as unreachable.
   */
  def markUnreachable(address: Address): ClusterMembership =
    copy(nodesUnreachable = nodesUnreachable + address)

  /**
   * Marks a previously unreachable node as reachable again.
   *
   * @param address
   * The address of the node that has become reachable again.
   * @return
   * A new [[ClusterMembership]] with the node marked as reachable.
   */
  def markReachable(address: Address): ClusterMembership =
    copy(nodesUnreachable = nodesUnreachable - address)

  /**
   * The number of nodes that are currently reachable.
   */
  def available: Int =
    nodesUp.size - nodesUnreachable.size

  /**
   * The total number of nodes currently known to be part of the cluster.
   */
  def total: Int =
    nodesUp.size

  /**
   * True if master is unreachable
   */
  def isSeedReachable: Boolean =
    master.exists(nodesUnreachable.contains)

/**
 * An empty cluster membership.
 */
object ClusterMembership {
  val empty: ClusterMembership =
    ClusterMembership(None, Set.empty, Set.empty)
}
