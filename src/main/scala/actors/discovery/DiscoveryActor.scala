package actors.discovery

import actors.gossip.GossipActor.GossipCommand
import actors.discovery.DiscoveryProtocol.{DiscoveryCommand, ListingUpdated, NodesRefRequest, NodesRefResponse, NotifyAddNode, NotifyRemoveNode, RegisterGossip, RegisterGossipPermit}
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
   * @return the actor's behavior
   */
  def apply(state: GossipPeerState): Behavior[DiscoveryCommand] =
    Behaviors.setup { context =>
      val adapter = context.messageAdapter[Receptionist.Listing](ListingUpdated.apply)
      context.system.receptionist ! Receptionist.Subscribe(GossipServiceKey, adapter)

      running(state, None, false)
    }

  private def running(
    state: GossipPeerState,
    gossip: Option[ActorRef[GossipCommand]],
    registerGossipPermit: Boolean
  ): Behavior[DiscoveryCommand] =
    Behaviors.receive { (context, message) =>
      message match {

        case RegisterGossip(gossipRef) if gossip.isEmpty =>
          if registerGossipPermit then
            context.system.receptionist ! Receptionist.Register(GossipServiceKey, gossipRef)
          running(state, Some(gossipRef), registerGossipPermit)

        case RegisterGossipPermit  =>
          if gossip.isDefined then
            context.system.receptionist ! Receptionist.Register(GossipServiceKey, gossip.get)
          running(state, gossip, true)

        case NodesRefRequest(replyTo) =>
          replyTo ! state.acceptedReferences.toList
          Behaviors.same

        case ListingUpdated(GossipServiceKey.Listing(all)) =>
          running(state.updateKnownReferences(all), gossip, registerGossipPermit)

        case NotifyAddNode(node) =>
          running(state.acceptNode(node), gossip, registerGossipPermit)


        case NotifyRemoveNode(node) =>
          running(state.removeNode(node), gossip, registerGossipPermit)
      }
    }
