package actors.gossip

import akka.actor.typed.ActorRef
import domain.data.LabeledPoint2D
import domain.network.Model

/**
 * Defines the public API and data structures for the Gossip component.
 */
object GossipProtocol:

  /**
   * Root trait for all messages handled by the GossipActor.
   */
  sealed trait GossipCommand extends Serializable

  /**
   * Commands that are broadcast to all application nodes
   */
  sealed trait ControlCommand extends GossipCommand

  object ControlCommand:

    /** Signal to start the simulation globally. */
    case object GlobalStart extends ControlCommand

    /** Signal to pause the simulation globally. */
    case object GlobalPause extends ControlCommand

    /** Signal to resume the simulation globally. */
    case object GlobalResume extends ControlCommand

    /** Signal to stop the simulation and cleanup globally. */
    case object GlobalStop extends ControlCommand

  /** Protocol for the GossipActor. */
  object GossipCommand:

    /** Starts the periodic gossip timer. */
    case object StartGossipTick extends GossipCommand

    /** Starts the periodic gossip timer. */
    case object StopGossipTick extends GossipCommand

    /**
     * Triggered periodically to request the list of active peers from the ClusterManager
     * and pass the found peers to WrappedPeers
     *
     */
    case object TickGossip extends GossipCommand

    /**
     *
     * Command for the Master node to distribute the dataset to the cluster peers.
     *
     * @param trainSet The complete training dataset.
     * @param testSet  The complete test dataset.
     */
    final case class DistributeDataset(trainSet: List[LabeledPoint2D], testSet: List[LabeledPoint2D]) extends GossipCommand

    /**
     *
     * Internal wrapper for dataset distribution after peer discovery.
     *
     * @param peers Peer to send the portion of the dataset.
     * @param trainSet The complete training dataset.
     * @param testSet The complete test dataset.
     * */
    final case class WrappedDistributeDataset(peers: List[ActorRef[GossipCommand]], trainSet: List[LabeledPoint2D], testSet: List[LabeledPoint2D]) extends GossipCommand

    /**
     * Message received by a Client node containing its portion of the data.
     *
     * @param trainShard The slice of training data for the local node.
     * @param testSet    The full test set for local evaluation.
     */
    final case class HandleDistributeDataset(trainShard: List[LabeledPoint2D], testSet: List[LabeledPoint2D]) extends GossipCommand

    /**
     * Wrapper for the list of discovered peers from the ClusterManager.
     * Select a peer at random and pass the local model to it using SendModelToPeer.
     *
     * @param peers The list of discovered peers.
     * */
    final case class WrappedPeers(peers: List[ActorRef[GossipCommand]]) extends GossipCommand

    /**
     * Handles a model received from a remote peer.
     *
     * @param remoteModel The model state received via gossip protocol.
     */
    final case class HandleRemoteModel(remoteModel: Model) extends GossipCommand

    /**
     * Sends the local model to a specific target peer.
     *
     * @param model  The local model snapshot.
     * @param target The ActorRef of the remote peer.
     */
    final case class SendModelToPeer(model: Model, target: ActorRef[GossipCommand]) extends GossipCommand

    /**
     * Broadcast a ControlCommand to all peers
     *
     * @param cmd The control command to spread.
     */
    final case class SpreadCommand(cmd: ControlCommand) extends ControlCommand

    /**
     * Internal wrapper to handle peer list for SpreadCommand
     *
     * @param peers The list of discovered peers.
     * @param cmd The control command to spread.
     * */
    final case class WrappedSpreadCommand(peers: List[ActorRef[GossipCommand]], cmd: ControlCommand) extends ControlCommand

    /**
     * Executes a control command received from a peer.
     *
     * @param cmd The control command to execute locally.
     */
    final case class HandleControlCommand(cmd: ControlCommand) extends ControlCommand
