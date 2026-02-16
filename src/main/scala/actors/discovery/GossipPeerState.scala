package actors.discovery

import actors.gossip.GossipActor.GossipCommand
import akka.actor.Address
import akka.actor.typed.ActorRef


/**
 * Represents the discovered gossip references and keeps tracks of the valid ones.
 *
 *  - [[knownReferences]]: every [[akka.actor.typed.ActorRef]] discovered through
 *    [[akka.actor.typed.receptionist.Receptionist]] with the local gossip actor included.
 *  - [[acceptedNodes]]: set of [[akka.actor.Address]] of valid nodes.
 */
final case class GossipPeerState(
                                   knownReferences: Set[ActorRef[GossipCommand]],
                                   acceptedNodes: Set[Address]
                                 ) :

  /**
   * Marks a node as accepted.
   *
   * @param address the address of the node to be accepted
   * @return new state with the new node included
   */
  def acceptNode(address: Address): GossipPeerState =
    copy(acceptedNodes = acceptedNodes + address)

  /**
   * Removes a node from the list of the accepted ones.
   *
   * @param address the address of the node to be removed
   * @return new state without the node
   */
  def removeNode(address: Address): GossipPeerState =
    copy(acceptedNodes = acceptedNodes - address)

  /**
   * Updates the set of known actor references.+
   * @param actorReferences the set of actor references
   * @return new state with the updated set
   */
  def updateKnownReferences(actorReferences: Set[ActorRef[GossipCommand]]): GossipPeerState =
    copy(knownReferences = actorReferences)


  /**
   * The set of actor references of the valid nodes.
   */
  def acceptedReferences: Set[ActorRef[GossipCommand]] = knownReferences.filter(ref => acceptedNodes.contains(ref
    .path.address) || ref.path.address.hasLocalScope)


object GossipPeerState:
  /** An initially empty state. */
  val empty: GossipPeerState =
    GossipPeerState(Set.empty, Set.empty)