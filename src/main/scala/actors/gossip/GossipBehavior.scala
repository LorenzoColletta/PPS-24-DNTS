package actors.gossip

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}
import actors.gossip.GossipActor.{ControlCommand, GossipCommand}
import actors.model.ModelActor.ModelCommand
import actors.discovery.DiscoveryProtocol.{DiscoveryCommand, NodesRefRequest}
import actors.root.RootProtocol.RootCommand
import actors.trainer.TrainerActor.TrainerCommand
import actors.gossip.configuration.ConfigurationProtocol
import actors.gossip.consensus.ConsensusProtocol
import actors.gossip.dataset_distribution.DatasetDistributionProtocol
import actors.gossip.dataset_distribution.DatasetDistributionProtocol.DatasetDistributionCommand
import config.AppConfig
import domain.network.Model

import scala.util.Random


/**
 * Encapsulates the behavior logic for the GossipActor.
 *
 * @param rootActor                  Reference to the local [[RootActor]].
 * @param modelActor                 Reference to the local [[ModelActor]].
 * @param trainerActor               Reference to the local [[TrainerActor]].
 * @param discoveryActor             Reference to the [[DiscoveryActor]] for peer discovery.
 * @param configurationActor         Reference to the local [[ConfigurationActor]].
 * @param datasetDistributionActor   Reference to the local [[DatasetDistributionActor]].
 * @param consensusActor             Reference to the local [[ConsensusActor]].
 * @param timers                     The scheduler for managing periodic gossip ticks.
 * @param config                     Global application configuration.
 */
private[gossip] class GossipBehavior(
  rootActor: ActorRef[RootCommand],
  modelActor: ActorRef[ModelCommand],
  trainerActor: ActorRef[TrainerCommand],
  discoveryActor: ActorRef[DiscoveryCommand],
  configurationActor: ActorRef[ConfigurationProtocol.ConfigurationCommand],
  datasetDistributionActor: ActorRef[DatasetDistributionCommand],
  consensusActor: ActorRef[ConsensusProtocol.ConsensusCommand],
  timers: TimerScheduler[GossipCommand],
  config: AppConfig
):

  /**
   * Distributes incoming messages to specialized sub-components or handles
   * them as general gossip via processGossipMessage
   *
   * @return The next behavior of the actor
   */
  private[gossip] def active(): Behavior[GossipCommand] =
    Behaviors.receive: (context, message) =>
      message match
        case cmd: GossipCommand.HandleControlCommand =>
          processGossipMessage(context, cmd)

        case configCmd: ConfigurationProtocol.ConfigurationCommand =>
          context.log.debug(s"Gossip: Routing configuration command to ConfigurationActor")
          configurationActor ! configCmd
          Behaviors.same

        case distCmd: DatasetDistributionProtocol.DatasetDistributionCommand =>
          context.log.debug("Gossip: Routing dataset distribution command to DatasetDistributionActor")
          datasetDistributionActor ! distCmd
          Behaviors.same

        case consCmd: ConsensusProtocol.ConsensusCommand =>
          context.log.debug("Gossip: Routing consensus command to ConsensusActor")
          consensusActor ! consCmd
          Behaviors.same

        case other =>
          processGossipMessage(context, other)

  /**
   *
   * Implements the core logic of the Gossip protocol and handles simulation control commands.
   *
   * This method manages:
   * - The periodic gossip cycle (requesting peers and syncing models).
   * - Model exchange between nodes (pushing/pulling model states).
   * - Global simulation control (Pause, Resume, Stop) propagated via the cluster.
   *
   * @param context The actor context.
   * @param message The specific [[GossipCommand]] to process.
   * @return The behavior resulting from the message processing
   */
  private def processGossipMessage(context: ActorContext[GossipCommand], message: GossipCommand): Behavior[GossipCommand] =
    message match
      case GossipCommand.StartGossipTick =>
        context.log.info("Gossip: Received Start signal. Starting gossip polling.")
        timers.startTimerWithFixedDelay(
          GossipCommand.TickGossip,
          GossipCommand.TickGossip,
          config.gossipInterval
        )
        Behaviors.same
      case GossipCommand.StopGossipTick =>
        context.log.info("Gossip: Stopping gossip polling.")
        timers.cancel(GossipCommand.TickGossip)
        Behaviors.same

      case GossipCommand.TickGossip =>
        discoveryActor ! NodesRefRequest(
          replyTo = context.messageAdapter(peers => GossipCommand.WrappedPeers(peers))
        )

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

      case GossipCommand.SpreadCommand(cmd) =>
        discoveryActor ! NodesRefRequest(
          replyTo = context.messageAdapter(peers =>
            GossipCommand.WrappedSpreadCommand(peers, cmd)
          )
        )
        Behaviors.same

      case GossipCommand.SpreadCommandOther(cmd) =>
        discoveryActor ! NodesRefRequest(
          replyTo = context.messageAdapter(peers =>
            val otherPeers = peers.filter(_ != context.self)
            GossipCommand.WrappedSpreadCommand(otherPeers, cmd)
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
            Behaviors.same
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