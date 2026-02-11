package actors.cluster.adapter

import actors.cluster.ClusterNode
import akka.cluster.Member

/**
 * Utility for converting Akka Cluster [[Member]] instances
 * into domain-level [[ClusterNode]] values.
 */
object ClusterNodeAdapter:

  def fromMember(member: Member): ClusterNode =
    ClusterNode(
      address = member.address,
      roles   = member.roles
    )

