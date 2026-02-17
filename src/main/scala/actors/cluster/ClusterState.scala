package actors.cluster

import actors.cluster.membership.ClusterMembership
import actors.root.RootProtocol.NodeRole
import akka.actor.Address

sealed trait Phase
case object Bootstrap extends Phase
case object Joining extends Phase
case object Running extends Phase

/**
 * Cluster state from the current node perspective.
 * @param phase application phase
 * @param role role of current node
 * @param view current node's view on cluster membership
 */
final case class ClusterState(
  phase: Phase,
  role: NodeRole,
  view: ClusterMembership,
  selfAddress: Option[Address]
)

object ClusterState:
  def initialState(role: NodeRole): ClusterState =
    ClusterState(Bootstrap, role, ClusterMembership.empty, None)