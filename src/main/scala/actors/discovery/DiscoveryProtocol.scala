package actors.discovery

import actors.gossip.GossipProtocol.GossipCommand
import akka.actor.Address
import akka.actor.typed.ActorRef
import akka.actor.typed.receptionist.Receptionist

/**
 * Defines the internal protocol of messages handled by the discovery actor's behavior.
 */
object DiscoveryProtocol :

  /**
   * Root trait for all messages handled by the DiscoveryActor.
   */
  sealed trait DiscoveryCommand

  /**
   * A Receptionist.listing wrapper.
   * @param listing the list of all [[ActorRef]] discovered.
   */
  final case class ListingUpdated(listing: Receptionist.Listing) extends DiscoveryCommand

  /**
   * Signals a request of the current known accessible [[ActorRef]] discovered.
   * @param replyTo the actor to send the list to
   */
  final case class NodesRefRequest(replyTo: ActorRef[List[ActorRef[GossipCommand]]]) extends DiscoveryCommand

  /**
   * Signals a response with the current known accessible [[ActorRef]] discovered.
   *
   * @param peers the list of [[ActorRef]]
   */
  final case class NodesRefResponse(peers: List[ActorRef[GossipCommand]])

  /**
   * Signals that a new node is accessible.
   * 
   * @param node the address of the node considered accessible
   */
  final case class NotifyAddNode(node: Address) extends  DiscoveryCommand

  /**
   * Signals that a node is no longer considered accessible.
   * 
   * @param node the address of the node no longer accessible
   */
  final case class NotifyRemoveNode(node: Address) extends DiscoveryCommand
