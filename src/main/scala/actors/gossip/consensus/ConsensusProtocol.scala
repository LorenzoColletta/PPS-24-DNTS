package actors.gossip.consensus

import akka.actor.typed.ActorRef
import domain.network.Model
import actors.gossip.GossipProtocol.GossipCommand

object ConsensusProtocol:

  sealed trait ConsensusCommand extends GossipCommand

  case object StartTickConsensus extends ConsensusCommand

  /**
   * Triggered periodically to start a consensus round by collecting all peer models.
   */
  case object TickConsensus extends ConsensusCommand

  case object StopTickConsensus extends ConsensusCommand

  /**
   * Wrapper for the list of discovered peers used for a consensus round.
   * Initiates model requests to ALL peers (not just one at random).
   *
   * @param peers The list of discovered peers.
   */
  final case class WrappedPeersForConsensus(peers: List[ActorRef[ConsensusCommand]]) extends ConsensusCommand

  /**
   * Request sent to a peer asking it to share its current model for consensus computation.
   *
   * @param replyTo The actor that will collect the response.
   * @param roundId Unique identifier for the consensus round, used to correlate replies.
   */
  final case class RequestModelForConsensus(replyTo: ActorRef[ConsensusCommand], roundId: Long) extends ConsensusCommand

  /**
   * Response to [[RequestModelForConsensus]] carrying the peer's current model snapshot.
   *
   * @param model   The sender's current model.
   * @param roundId The consensus round this response belongs to.
   */
  final case class ConsensusModelReply(model: Model, roundId: Long) extends ConsensusCommand


  /**
   * Internal message carrying the local model snapshot for a consensus round,
   * together with the full list of peers that were contacted.
   *
   * @param localModel The local model at the moment the round was initiated.
   * @param peers      All peers that were asked to participate in this round.
   * @param roundId    Unique identifier for the round.
   */
  final case class WrappedLocalModelForConsensus(
                                                  localModel: Model,
                                                  peers: List[ActorRef[ConsensusCommand]],
                                                  roundId: Long
                                                ) extends ConsensusCommand

  /**
   * Accumulates peer model replies for a single consensus round.
   *
   * @param roundId       Unique identifier so stale replies from a previous
   *                      round are safely discarded.
   * @param expectedCount How many peers were contacted (= peers we are
   *                      waiting a reply from).
   * @param collected     Models received so far (from peers that replied in time).
   * @param localModel    Snapshot of the local model at round-start (always
   *                      included in the consensus computation).
   */
  final case class ConsensusRoundState(
                                        roundId: Long,
                                        expectedCount: Int,
                                        collected: List[Model],
                                        localModel: Model
                                      )