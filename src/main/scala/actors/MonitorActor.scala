package actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}

import actors.TrainerActor.{TrainerCommand, TrainingConfig}
import actors.ModelActor.ModelCommand
import actors.GossipActor.GossipCommand
import config.AppConfig

/**
 * Supervisor actor for the simulation lifecycle on a single node.
 * Acts as the main controller for the UI/CLI.
 */
object MonitorActor:

  /** Commands handled by the MonitorActor. */
  enum MonitorCommand:
    /** Starts the simulation setup (Master node only). Distributes data via Gossip. */
    case StartSimulation(config: TrainingConfig)

    /** Starts local training with a specific data slice, received by Master. */
    case StartWithData(config: TrainingConfig)

    /** Stops the simulation globally. */
    case StopSimulation

    /** Pauses the simulation globally. */
    case PauseSimulation

    /** Resumes the simulation globally. */
    case ResumeSimulation

    /** Triggers local metric collection. */
    case TickMetrics

    /** Response containing local metrics (Train Loss, Test Loss, and Consensus). */
    case MetricsResponse(trainLoss: Double, testLoss: Double, consensus: Double)

    /** Simulates a node crash (forces system termination). */
    case SimulateCrash

    /** Updates the count of active peers in the cluster. */
    case PeerCountChanged(active: Int, total: Int)

  /** Internal actor state */
  private case class MonitorState(
    peerCount: Int = 1,
    totalPeersDiscovered: Int = 1,
    isMaster: Boolean = false
  )


  /**
   * Creates the initial behavior for the MonitorActor.
   *
   * @param modelActor   Reference to the local ModelActor.
   * @param trainerActor Reference to the local TrainerActor.
   * @param gossipActor  Reference to the local GossipActor.
   * @param isMaster     If true, this node orchestrates the simulation start.
   * @param appConfig    Implicit application configuration.
   */
  def apply(
    modelActor: ActorRef[ModelCommand],
    trainerActor: ActorRef[TrainerCommand],
    gossipActor: ActorRef[GossipCommand],
    isMaster: Boolean = false
  )(using appConfig: AppConfig): Behavior[MonitorCommand] =

    Behaviors.setup: context =>
      Behaviors.withTimers: timers =>
        context.log.info("Monitor: Initialized and waiting for user commands.")
        waiting(modelActor, trainerActor, gossipActor, timers, MonitorState(isMaster = isMaster))

  private def waiting(
    ma: ActorRef[ModelCommand],
    ta: ActorRef[TrainerCommand],
    ga: ActorRef[GossipCommand],
    timers: TimerScheduler[MonitorCommand],
    state: MonitorState
  )(using appConfig: AppConfig): Behavior[MonitorCommand] =

    Behaviors.receive: (context, message) =>
      message match
        case MonitorCommand.StartSimulation(config) if state.isMaster =>
          context.log.info("Monitor: Starting configured simulation...")

          // val fullDataset = DatasetGenerator.generate(...)
          // val allSlices = DataSplitter.split(fullDataset, state.peerCount)

          // ga ! GossipCommand.DistributeData(allSlices.map(s => config.copy(dataset = s)))

          active(ma, ta, ga, timers, state)

        case MonitorCommand.StartWithData(config) =>
          context.log.info(s"Monitor: Received subsection of ${config.dataset.size} points. Start training...")

          ta ! TrainerCommand.Start(config)

          timers.startTimerAtFixedRate(MonitorCommand.TickMetrics, appConfig.metricsInterval)
          
          active(ma, ta, ga, timers, state)

        case MonitorCommand.PeerCountChanged(active, total) =>
          context.log.info(s"Cluster Status changed: $active/$total connected peer.")

          // TODO: GUI.updatePeerDisplay(active, total)
          waiting(ma, ta, ga, timers, state.copy(peerCount = active, totalPeersDiscovered = total))

        case _ => Behaviors.unhandled

  private def active(
    ma: ActorRef[ModelCommand],
    ta: ActorRef[TrainerCommand],
    ga: ActorRef[GossipCommand],
    timers: TimerScheduler[MonitorCommand],
    state: MonitorState
  )(using AppConfig): Behavior[MonitorCommand] =

    Behaviors.receive: (context, message) =>
      message match
        case MonitorCommand.TickMetrics =>
          ma ! ModelCommand.GetMetrics(replyTo = context.self)
          Behaviors.same

        case MonitorCommand.MetricsResponse(trainLoss, testLoss, consensus) =>
          context.log.info(s"Monitor Update - Train Loss: $trainLoss, Test Loss: $testLoss, Consensus Metric: $consensus")

          // TODO: GUI.plot(trainLoss, testLoss, consensus)
          Behaviors.same

        case MonitorCommand.PauseSimulation =>
          context.log.info("Monitor: User requested PAUSE. Propagating...")
          ga ! GossipCommand.SpreadCommand(GossipCommand.GlobalPause)
          Behaviors.same

        case MonitorCommand.ResumeSimulation =>
          context.log.info("Monitor: User requested RESUME. Propagating...")
          ga ! GossipCommand.SpreadCommand(GossipCommand.GlobalResume)
          Behaviors.same

        case MonitorCommand.SimulateCrash =>
          context.log.warn("Monitor: Crash simulation. Forced node shutdown.")

          // TODO: GUI.showCrashMessage()
          context.system.terminate()
          
          Behaviors.stopped

        case MonitorCommand.StopSimulation =>
          context.log.info("Monitor: User requested RESUME. Propagating...")
          ga ! GossipCommand.SpreadCommand(GossipCommand.GlobalStop)

          timers.cancelAll()
          waiting(ma, ta, ga, timers, state)

        case MonitorCommand.PeerCountChanged(activePeers, totalPeers) =>
          // TODO: GUI.updatePeerDisplay(activePeers, totalPeers)
          active(ma, ta, ga, timers, state.copy(peerCount = activePeers, totalPeersDiscovered = totalPeers))

        case _ => Behaviors.unhandled
