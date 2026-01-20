package actors.monitor

import actors.GossipActor.GossipCommand
import actors.ModelActor.ModelCommand
import actors.trainer.TrainerActor.TrainerCommand
import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import config.AppConfig
import domain.data.LabeledPoint2D
import view.{ViewBoundary, ViewStateSnapshot}

/**
 * Supervisor actor for the simulation lifecycle on a single node.
 * Acts as the main controller for the UI.
 */
object MonitorActor:

  export MonitorProtocol.*

  private case class MonitorState(
    snapshot: ViewStateSnapshot = ViewStateSnapshot(),
    isMaster: Boolean,
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
    boundary: ViewBoundary,
    isMaster: Boolean = false
  )(using appConfig: AppConfig): Behavior[MonitorCommand] =

    Behaviors.setup: context =>
      Behaviors.withTimers: timers =>
        context.log.info("Monitor: Initialized and waiting for user commands.")
        waiting(
          modelActor,
          trainerActor,
          gossipActor,
          timers,
          boundary,
          MonitorState(isMaster = isMaster)
        )

  private def waiting(
    ma: ActorRef[ModelCommand],
    ta: ActorRef[TrainerCommand],
    ga: ActorRef[GossipCommand],
    timers: TimerScheduler[MonitorCommand],
    boundary: ViewBoundary,
    state: MonitorState,
  )(using appConfig: AppConfig): Behavior[MonitorCommand] =

    Behaviors.receive: (context, message) =>
      message match
        case MonitorCommand.Initialize(seed, model, config) =>
          context.log.info(s"Monitor: Cluster successful created on seed: $seed")

          val newState = state.copy(
            snapshot = state.snapshot.copy(
              clusterSeed = Some(seed),
              model = Some(model),
              config = Some(config),
            )
          )
          boundary.showInitialScreen(newState.snapshot, state.isMaster)
          waiting(ma, ta, ga, timers, boundary, newState)

        case MonitorCommand.ConnectionFailed(reason) =>
          context.log.info(s"Monitor: Connection error: $reason")

          boundary.showInitialError(reason)
          Behaviors.stopped

        case MonitorCommand.StartSimulation(config) if state.isMaster =>
          context.log.info("Monitor: Starting configured simulation...")

          // val fullDataset = DatasetGenerator.generate(...)
          // val allSlices = DataSplitter.split(fullDataset, state.peerCount)

          // ga ! GossipCommand.DistributeData(allSlices.map(s => config.copy(dataset = s)))

          active(ma, ta, ga, timers, boundary, state)

        case MonitorCommand.StartWithData(trainSet, testSet) =>
          context.log.info(s"Monitor: Received subsection of ${trainSet.size + testSet.size} points. Start training...")

          val newState = state.copy(
            snapshot = state.snapshot.copy(
              config = Some(
                  state.snapshot.config.get.copy(
                  trainSet = trainSet,
                  testSet = testSet,
                )
              )
            )
          )
          boundary.startSimulation(newState.snapshot)

          timers.startTimerAtFixedRate(MonitorCommand.TickMetrics, appConfig.metricsInterval)
          
          active(ma, ta, ga, timers, boundary, newState)

        case MonitorCommand.PeerCountChanged(activePeers, totalPeers) =>
          context.log.info(s"Monitor: Cluster Status changed, $activePeers/$totalPeers connected peer.")

          boundary.updatePeerDisplay(activePeers, totalPeers)

          waiting(ma, ta, ga, timers, boundary,
            state.copy(
              snapshot = state.snapshot.copy(
                activePeers = activePeers,
                totalPeers = totalPeers
              )
            )
          )

        case _ => Behaviors.unhandled

  private def active(
    ma: ActorRef[ModelCommand],
    ta: ActorRef[TrainerCommand],
    ga: ActorRef[GossipCommand],
    timers: TimerScheduler[MonitorCommand],
    boundary: ViewBoundary,
    state: MonitorState,
  )(using AppConfig): Behavior[MonitorCommand] =

    Behaviors.receive: (context, message) =>
      message match
        case MonitorCommand.TickMetrics =>
          ma ! ModelCommand.GetMetrics(replyTo = context.self)
          Behaviors.same

        case MonitorCommand.ViewUpdateResponse(epoch, model, trainLoss, testLoss, consensus) =>
          context.log.info(s"Monitor Update - Train Loss: $trainLoss, Test Loss: $testLoss, Consensus Metric: $consensus")

          boundary.plotMetrics(epoch, trainLoss, testLoss, consensus)
          boundary.plotDecisionBoundary(model)

          active(ma, ta, ga, timers, boundary,
            state.copy(
              snapshot = state.snapshot.copy(
                epoch = epoch,
                model = Some(model),
                trainLoss = Some(trainLoss),
                testLoss = Some(testLoss),
                consensus = Some(consensus),
              )
            )
          )
          Behaviors.same

        case MonitorCommand.PauseSimulation =>
          context.log.info("Monitor: User requested PAUSE. Propagating...")
          ga ! GossipCommand.SpreadCommand(GossipCommand.GlobalPause)
          Behaviors.same

        case MonitorCommand.InternalPause =>
          context.log.info("Monitor: Remote PAUSE command.")
          boundary.setPausedState(true)
          Behaviors.same

        case MonitorCommand.ResumeSimulation =>
          context.log.info("Monitor: User requested RESUME. Propagating...")
          ga ! GossipCommand.SpreadCommand(GossipCommand.GlobalResume)
          Behaviors.same

        case MonitorCommand.InternalResume =>
          context.log.info("Monitor: Remote RESUME command.")
          boundary.setPausedState(false)
          Behaviors.same

        case MonitorCommand.SimulateCrash =>
          context.log.warn("Monitor: Crash simulation. Forced node shutdown.")

          boundary.showCrashMessage()

          context.system.terminate()
          Behaviors.stopped

        case MonitorCommand.StopSimulation =>
          context.log.info("Monitor: User requested RESUME. Propagating...")

          ga ! GossipCommand.SpreadCommand(GossipCommand.GlobalStop)

          Behaviors.same

        case MonitorCommand.InternalStop =>
          context.log.info("Monitor: Remote STOP command.")
          boundary.stopSimulation()

          timers.cancelAll()
          waiting(ma, ta, ga, timers, boundary, state)

        case MonitorCommand.PeerCountChanged(activePeers, totalPeers) =>
          boundary.updatePeerDisplay(activePeers, totalPeers)

          active(ma, ta, ga, timers, boundary,
            state.copy(
              snapshot = state.snapshot.copy(
                activePeers = activePeers,
                totalPeers = totalPeers,
              )
            )
          )
          Behaviors.same

        case MonitorCommand.RequestWeightsLog =>
          ma ! ModelCommand.ExportToFile()
          Behaviors.same

        case _ => Behaviors.unhandled
