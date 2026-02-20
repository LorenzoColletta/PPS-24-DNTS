package actors.gossip.consensus

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import actors.discovery.DiscoveryProtocol.{DiscoveryCommand, NodesRefRequest}
import ConsensusProtocol.*
import actors.model.ModelProtocol.ModelCommand
import config.AppConfig
import domain.network.Model
import domain.training.Consensus.divergenceFrom

private[consensus] class ConsensusBehavior(
  modelActor: ActorRef[ModelCommand],
  discoveryActor: ActorRef[DiscoveryCommand],
  timers: TimerScheduler[ConsensusCommand],
  config: AppConfig
):

  private[consensus] def active(
    consensusRound: Option[ConsensusRoundState] = None,
    roundCounter: Long = 0L
  ): Behavior[ConsensusCommand] =

    Behaviors.receive: (context, message) =>
      message match
        case StartTickConsensus =>
          timers.startTimerWithFixedDelay(
            TickConsensus,
            TickConsensus,
            config.consensusInterval
          )
          Behaviors.same
        case TickConsensus =>
          if consensusRound.isEmpty then
            context.log.debug("Gossip: Consensus tick")
            discoveryActor ! NodesRefRequest(
              replyTo = context.messageAdapter(peers => WrappedPeersForConsensus(peers))
            )
          Behaviors.same

        case StopTickConsensus =>
          timers.cancel(TickConsensus)
          Behaviors.same

        case WrappedPeersForConsensus(peers) =>
          val otherPeers = peers.filterNot(_.path.address.hasLocalScope)

          if otherPeers.isEmpty then
            context.log.debug("Gossip: Consensus – no remote peers, skipping round.")
            Behaviors.same
          else
            val newRoundId = roundCounter + 1
            context.log.info(s"Gossip: Starting consensus round #$newRoundId with ${peers.size} peers.")

            modelActor ! ModelCommand.GetModel(
              replyTo = context.messageAdapter(localModel =>
                WrappedLocalModelForConsensus(localModel, otherPeers, newRoundId)
              )
            )

            active(consensusRound, newRoundId)

        case WrappedLocalModelForConsensus(localModel, peers, roundId) =>
          val newRound = ConsensusRoundState(
            roundId = roundId,
            expectedCount = peers.size,
            collected = List(localModel),
            localModel = localModel
          )

          peers.foreach { peer =>
            peer ! RequestModelForConsensus(context.self, roundId)
          }

          timers.startSingleTimer(
            s"ConsensusTimeout-$roundId",
            ConsensusRoundTimeout(roundId),
            config.consensusInterval
          )

          context.log.debug(s"Gossip: Round #$roundId – contacted ${peers.size} peers.")
          active(Some(newRound), roundCounter)

        case RequestModelForConsensus(replyTo, roundId) =>
          modelActor ! ModelCommand.GetModel(
            replyTo = context.messageAdapter(model =>
              ForwardModelReply(replyTo, model, roundId)
            )
          )
          Behaviors.same

        case ForwardModelReply(replyTo, model, roundId) =>
          replyTo ! ConsensusModelReply(model, roundId)
          Behaviors.same

        case ConsensusProtocol.Stop =>
          context.log.info("Consensus: Stopping actor.")
          timers.cancelAll()
          Behaviors.stopped

        case ConsensusRoundTimeout(roundId) =>
          consensusRound match
            case Some(round) if round.roundId == roundId =>
              context.log.warn(
                s"Gossip: Round #$roundId TIMED OUT. " +
                  s"Computing consensus with partial data (${round.collected.size}/${round.expectedCount + 1} models)."
              )
              val consensusValue = computeNetworkConsensus(round.localModel, round.collected)
              modelActor ! ModelCommand.UpdateConsensus(consensusValue)

              active(None, roundCounter)

            case _ =>
              Behaviors.same

        case ConsensusModelReply(model, roundId) =>
          consensusRound match
            case Some(round) if round.roundId == roundId =>
              val updated = round.copy(collected = model :: round.collected)

              if updated.collected.size >= updated.expectedCount + 1 then
                timers.cancel(s"ConsensusTimeout-$roundId")

                val consensusValue = computeNetworkConsensus(updated.localModel, updated.collected)
                context.log.info(
                  f"Gossip: Round #$roundId complete. " +
                    f"Network consensus: $consensusValue%.6f"
                )
                
                modelActor ! ModelCommand.UpdateConsensus(consensusValue)
                active(None, roundCounter)
              else
                context.log.debug(
                  s"Gossip: Round #$roundId – " +
                    s"${updated.collected.size}/${updated.expectedCount + 1} models collected."
                )
                active(Some(updated), roundCounter)

            case Some(round) =>
              context.log.debug(
                s"Gossip: Discarding stale ConsensusModelReply for round #$roundId " +
                  s"(current round is #${round.roundId})."
              )
              Behaviors.same

            case None =>
              context.log.debug(
                s"Gossip: No active consensus round. Discarding reply for round #$roundId."
              )
              Behaviors.same


private def computeNetworkConsensus(localModel: Model, allModels: List[Model]): Double =
  if allModels.isEmpty then 0.0
  else
    val divergences = allModels.map(m => localModel.network divergenceFrom m.network)
    divergences.sum / divergences.size

