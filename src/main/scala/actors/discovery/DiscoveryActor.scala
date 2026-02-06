package actors.discovery

import actors.gossip.GossipActor.GossipCommand
import actors.discovery.DiscoveryProtocol.{DiscoveryCommand, ListingUpdated, NodesRefRequest, NodesRefResponse, NotifyAddNode, NotifyRemoveNode}
import actors.gossip.GossipProtocol.GossipCommand.WrappedPeers
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors

/**
 * Actor responsible for the management of the list of [[ActorRef]] considered accessible.
 */
object DiscoveryActor:

  private val GossipServiceKey: ServiceKey[GossipCommand] =
    ServiceKey[GossipCommand]("gossip-service")

  /**
   * Creates the DiscoveryActor behavior.
   *
   * @param state     the initial state
   * @param gossip    the gossip actor
   * @return the actor's behavior
   */
  def apply(
    state: GossipPeerState,
    gossip: ActorRef[GossipCommand]
  ): Behavior[DiscoveryCommand] =
    Behaviors.setup { context =>

      context.system.receptionist ! Receptionist.Register(GossipServiceKey, gossip)

      val adapter = context.messageAdapter[Receptionist.Listing](ListingUpdated.apply)

      context.system.receptionist ! Receptionist.Subscribe(GossipServiceKey, adapter)

      Behaviors.receiveMessage {
        case ListingUpdated(GossipServiceKey.Listing(all)) =>
          val newState = state.updateKnownReferences(all)
          DiscoveryActor(newState, gossip)

        case NodesRefRequest(replyTo) =>
          replyTo ! state.acceptedReferences.toList
          Behaviors.same

        case NotifyAddNode(node) =>
          val newState = state.acceptNode(node)
          DiscoveryActor(newState, gossip)

        case NotifyRemoveNode(node) =>
          val newState = state.removeNode(node)
          DiscoveryActor(newState, gossip)

      }
    }
