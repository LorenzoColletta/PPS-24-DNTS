package actors.gossip

import akka.actor.typed.ActorRef
import domain.network.Model

/**
 * Defines the messaging protocol for the Gossip component.
 */
object GossipProtocol:
  enum GossipCommand:
    /** Internal trigger to start a new gossip round. */
    case TickGossip

    /** Received when a remote peer sends its model for synchronization. */
    case HandleRemoteModel(remoteModel: Model)

    /** Propagates a control command to all other peers in the cluster. */
    case SpreadCommand(cmd: ControlCommand)

    /** Handles a control command received from a peer. */
    case HandleControlCommand(cmd: ControlCommand)

    /** Updates the local list of available gossip peers. */
    case UpdatePeers(peers: Set[ActorRef[GossipCommand]])

  /** Global simulation control signals. */
  enum ControlCommand:
    case GlobalPause
    case GlobalResume
    case GlobalStop