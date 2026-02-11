package actors.cluster

import akka.actor.Address

/**
 * Domain representation of a cluster node.
 *
 * @param address the node address in the cluster
 * @param roles   the set of roles associated with the node
 */
final case class ClusterNode(
  address: Address,
  roles: Set[String]
)





