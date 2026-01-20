package actors.cluster

import akka.actor.Address

/**
 * Represents the local view of the cluster membership from the perspective
 * of this node.
 *
 * @param peers
 * The set of all peers that are currently part of the cluster,
 * including reachable and unreachable ones.
 * @param unreachable
 * The subset of [[peers]] that are currently considered unreachable.
 */
final case class ClusterMembership(
  peers: Set[Address],
  unreachable: Set[Address]
):

  /**
   * Adds a new peer to the cluster membership. Adding an already known peer
   * does not change the resulting membership.
   *
   * @param address
   * The address of the peer that has joined the cluster.
   * @return
   * A new [[ClusterMembership]] including the given peer.
   */
  def addPeer(address: Address): ClusterMembership =
    copy(peers = peers + address)

  /**
   * Removes a peer from the cluster membership.
   *
   * @param address
   * The address of the peer that has left or has been removed from the cluster.
   * @return
   * A new [[ClusterMembership]] without the given peer.
   */
  def removePeer(address: Address): ClusterMembership =
    copy(
      peers = peers - address,
      unreachable = unreachable - address
    )

  /**
   * Marks a peer as unreachable.
   *
   * @param address
   * The address of the peer that has become unreachable.
   * @return
   * A new [[ClusterMembership]] with the peer marked as unreachable.
   */
  def markUnreachable(address: Address): ClusterMembership =
    copy(unreachable = unreachable + address)

  /**
   * Marks a previously unreachable peer as reachable again.
   *
   * @param address
   * The address of the peer that has become reachable again.
   * @return
   * A new [[ClusterMembership]] with the peer marked as reachable.
   */
  def markReachable(address: Address): ClusterMembership =
    copy(unreachable = unreachable - address)

  /**
   * The number of peers that are currently reachable.
   */
  def available: Int =
    peers.size - unreachable.size

  /**
   * The total number of peers currently known to be part of the cluster.
   */
  def total: Int =
    peers.size

/**
 * An empty cluster membership.
 */
object ClusterMembership {
  val empty: ClusterMembership =
    ClusterMembership(Set.empty, Set.empty)
}
