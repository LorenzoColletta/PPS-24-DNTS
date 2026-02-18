package actors.gossip

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import actors.gossip.GossipActor.{ControlCommand, GossipCommand}
import actors.model.ModelActor.ModelCommand
import actors.discovery.DiscoveryProtocol.{DiscoveryCommand, NodesRefRequest}
import actors.trainer.TrainerActor.TrainerCommand
import actors.trainer.TrainerActor.TrainingConfig
import actors.root.RootActor.RootCommand
import akka.actor.typed.scaladsl.TimerScheduler
import config.AppConfig
import domain.model.ModelTasks
import domain.network.Model
import domain.training.Consensus.divergenceFrom

import scala.util.Random

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
private[gossip] case class ConsensusRoundState(
                                        roundId: Long,
                                        expectedCount: Int,
                                        collected: List[Model],
                                        localModel: Model
                                      )

/**
 * Encapsulates the behavior logic for the GossipActor.
 *
 * @param modelActor     Reference to the local ModelActor.
 * @param trainerActor   Reference to the local TrainerActor.
 * @param timers         The scheduler for managing periodic gossip ticks.
 * @param config         Global application configuration.
 */
private[gossip] class GossipBehavior(
  rootActor: ActorRef[RootCommand],
  modelActor: ActorRef[ModelCommand],
  trainerActor: ActorRef[TrainerCommand],
  discoveryActor: ActorRef[DiscoveryCommand],
  timers: TimerScheduler[GossipCommand],
  config: AppConfig
):

  private[gossip] def active(
                              cachedConfig: Option[(String, Model, TrainingConfig)] = None,
                              consensusRound: Option[ConsensusRoundState] = None,
                              roundCounter: Long = 0L
                            ): Behavior[GossipCommand] =

      Behaviors.receive: (context, message) =>
        message match

          case GossipCommand.ShareConfig(seedID, model, trainConfig) =>
            context.log.info("Gossip: Received Config from Root. Broadcasting to Cluster...")

            val cmd = ControlCommand.PrepareClient(seedID, model, trainConfig)

            discoveryActor ! NodesRefRequest(replyTo =
              context.messageAdapter(peers => GossipCommand.WrappedSpreadCommand(peers, cmd))
            )

            active(Some((seedID, model, trainConfig)), consensusRound, roundCounter)

          case GossipCommand.StartGossipTick =>
            context.log.info("Gossip: Received Start signal. Starting gossip polling.")
            timers.startTimerWithFixedDelay(
              GossipCommand.TickGossip,
              GossipCommand.TickGossip,
              config.gossipInterval
            )
            Behaviors.same
          case GossipCommand.StartTickConsensus =>
            timers.startTimerWithFixedDelay(
              GossipCommand.TickConsensus,
              GossipCommand.TickConsensus,
              config.consensusInterval
            )

            Behaviors.same
          case GossipCommand.StartTickRequest =>
            timers.startTimerWithFixedDelay(
              GossipCommand.TickRequest,
              GossipCommand.TickRequest,
              config.gossipRequestConfig
            )
            Behaviors.same

          case GossipCommand.StopGossipTick =>
            context.log.info("Gossip: Stopping gossip polling.")
            timers.cancel(GossipCommand.TickGossip)
            Behaviors.same

          case GossipCommand.StopTickConsensus =>
            timers.cancel(GossipCommand.TickConsensus)
            Behaviors.same

          case GossipCommand.StopTickRequest =>
            timers.cancel(GossipCommand.TickRequest)
            Behaviors.same

          case GossipCommand.TickGossip =>
            discoveryActor ! NodesRefRequest(
              replyTo = context.messageAdapter(peers => GossipCommand.WrappedPeers(peers))
            )

            Behaviors.same

          case GossipCommand.TickRequest =>
            if cachedConfig.isEmpty then
              context.log.debug("Gossip: Not initialized yet. Asking peers for Config...")
              discoveryActor ! NodesRefRequest(
                replyTo = context.messageAdapter(peers => GossipCommand.WrappedRequestConfig(peers.toSet))
              )
            Behaviors.same

          case GossipCommand.WrappedRequestConfig(peers) =>
            peers.filter(_ != context.self).foreach { peer =>
              peer ! GossipCommand.RequestInitialConfig(context.self)
            }
            Behaviors.same

          case GossipCommand.RequestInitialConfig(replyTo) =>
            cachedConfig match
              case Some((seedID, model, trainConfig)) =>
                context.log.info(s"Gossip: Peer $replyTo asked for config. Sending it.")
                replyTo ! GossipCommand.HandleControlCommand(
                  ControlCommand.PrepareClient(seedID, model, trainConfig)
                )
              case None =>
                context.log.info(s"Config not found.")
            Behaviors.same

          case GossipCommand.DistributeDataset(trainSet , testSet) =>
            discoveryActor ! NodesRefRequest(
              replyTo = context.messageAdapter(peers =>
                GossipCommand.WrappedDistributeDataset(peers, trainSet, testSet)
              )
            )
            Behaviors.same

          case GossipCommand.WrappedDistributeDataset(peers, trainSet, testSet)  =>
            val totalNodes = peers.size
            if totalNodes > 0 then
              val chunkSize = trainSet.size / totalNodes

              peers.zipWithIndex.foreach: (peer, index) =>
                val from = index * chunkSize
                val until = if (index == totalNodes - 1) trainSet.size else (index + 1) * chunkSize

                val trainShard = trainSet.slice(from, until)

                peer ! GossipCommand.HandleDistributeDataset(trainShard, testSet)
            Behaviors.same

          case GossipCommand.HandleDistributeDataset(trainShard, testSet) =>
            rootActor ! RootCommand.DistributedDataset(trainShard, testSet)
            Behaviors.same

          case GossipCommand.WrappedPeers(peers) =>
            val potentialPeers = peers.filter(_ != context.self)
            if potentialPeers.nonEmpty then
              val target = potentialPeers(Random.nextInt(potentialPeers.size))
              modelActor ! ModelCommand.GetModel(
                replyTo = context.messageAdapter(model => GossipCommand.SendModelToPeer(model, target))
              )
            Behaviors.same

          case GossipCommand.SendModelToPeer(model, target) =>
            context.log.info(s"Gossip: Sending Model  to peer $target")
            target ! GossipCommand.HandleRemoteModel(model)
            Behaviors.same

          case GossipCommand.HandleRemoteModel(remoteModel) =>
            modelActor ! ModelCommand.SyncModel(remoteModel)
            Behaviors.same

          case GossipCommand.TickConsensus =>
            context.log.debug("Gossip: Consensus tick")
            discoveryActor ! NodesRefRequest(
              replyTo = context.messageAdapter(peers => GossipCommand.WrappedPeersForConsensus(peers))
            )
            Behaviors.same

          case GossipCommand.WrappedPeersForConsensus(peers) =>

            if peers.isEmpty then
              context.log.debug("Gossip: Consensus – no remote peers, skipping round.")
              Behaviors.same
            else
              val newRoundId = roundCounter + 1
              context.log.info(s"Gossip: Starting consensus round #$newRoundId with ${peers.size} peers.")

              modelActor ! ModelCommand.GetModel(
                replyTo = context.messageAdapter(localModel =>
                  GossipCommand.WrappedLocalModelForConsensus(localModel, peers, newRoundId)
                )
              )

              active(cachedConfig, consensusRound, newRoundId)

          case GossipCommand.WrappedLocalModelForConsensus(localModel, peers, roundId) =>
            val newRound = ConsensusRoundState(
              roundId       = roundId,
              expectedCount = peers.size,
              collected     = List(localModel),
              localModel    = localModel
            )

            peers.foreach { peer =>
              peer ! GossipCommand.RequestModelForConsensus(context.self, roundId)
            }

            context.log.debug(s"Gossip: Round #$roundId – contacted ${peers.size} peers.")
            active(cachedConfig, Some(newRound), roundCounter)

          case GossipCommand.RequestModelForConsensus(replyTo, roundId) =>
            modelActor ! ModelCommand.GetModel(
              replyTo = context.messageAdapter(model =>
                GossipCommand.ConsensusModelReply(model, roundId)
              )
            )
            Behaviors.same

          case GossipCommand.ConsensusModelReply(model, roundId) =>
            consensusRound match
              case Some(round) if round.roundId == roundId =>
                val updated = round.copy(collected = model :: round.collected)

                if updated.collected.size >= updated.expectedCount + 1 then
                  val consensusValue = computeNetworkConsensus(updated.localModel, updated.collected)
                  context.log.info(
                    f"Gossip: Round #$roundId complete. " +
                      f"Network consensus (mean divergence from centroid): $consensusValue%.6f"
                  )
                  modelActor ! ModelCommand.UpdateConsensus(consensusValue)
                  active(cachedConfig, None, roundCounter)
                else
                  context.log.debug(
                    s"Gossip: Round #$roundId – " +
                      s"${updated.collected.size}/${updated.expectedCount + 1} models collected."
                  )
                  active(cachedConfig, Some(updated), roundCounter)

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
          case GossipCommand.SpreadCommand(cmd) =>
            discoveryActor ! NodesRefRequest(
              replyTo = context.messageAdapter(peers =>
                GossipCommand.WrappedSpreadCommand(peers, cmd)
              )
            )
            Behaviors.same

          case GossipCommand.WrappedSpreadCommand(peers, cmd) =>
            peers.foreach ( peer =>
              peer ! GossipCommand.HandleControlCommand(cmd)
            )
            Behaviors.same

          case GossipCommand.HandleControlCommand(cmd) =>
            context.log.info(s"Gossip: Executing remote control command: $cmd")

            cmd match
              case ControlCommand.PrepareClient(seedID, model, trainConfig) =>
                context.log.info(s"Gossip (CLIENT): Received Init Config from $seedID")
                rootActor ! RootCommand.ConfirmInitialConfiguration(seedID, model, trainConfig)
                active(Some((seedID, model, trainConfig)))
              case ControlCommand.GlobalPause =>
                trainerActor ! TrainerCommand.Pause
                Behaviors.same
              case ControlCommand.GlobalResume =>
                trainerActor ! TrainerCommand.Resume
                Behaviors.same
              case ControlCommand.GlobalStop =>
                rootActor ! RootCommand.StopSimulation

                timers.cancelAll()
                Behaviors.stopped
              case _ =>
                context.log.info(s"Gossip: Not found remote control command: $cmd")
                Behaviors.same

          case _ =>
            context.log.warn("Gossip: Received unhandled gossip message.")
            Behaviors.unhandled

  private def computeNetworkConsensus(localModel: Model, allModels: List[Model]): Double =
    if allModels.isEmpty then 0.0
    else
      val divergences = allModels.map(m => localModel.network divergenceFrom m.network)
      divergences.sum / divergences.size

  private def buildCentroid(models: List[Model]): Model =
    models.tail.foldLeft(models.head): (runningMean, nextModel) =>
      val (centroid, _) = ModelTasks.mergeWith(nextModel).run(runningMean)
      centroid