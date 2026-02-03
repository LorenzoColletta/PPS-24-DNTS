package actors.monitor

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import config.AppConfig
import view.{ViewBoundary, ViewStateSnapshot}
import actors.gossip.GossipActor.{GossipCommand, ControlCommand}
import actors.root.RootActor.RootCommand
import actors.monitor.MonitorProtocol._
import actors.model.ModelActor.ModelCommand

/**
 * Encapsulates the behavior logic for the MonitorActor.
 *
 * @param timers        The scheduler for managing timed messages.
 * @param modelActor    Reference to the local ModelActor.
 * @param gossipActor   Reference to the local GossipActor.
 * @param rootActor     Reference to the local RootActor.
 * @param boundary      The abstraction acting as a bridge to the View.
 * @param isMaster      If true, this node orchestrates the simulation start.
 * @param appConfig     Implicit application global configuration.
 */
private[monitor] class MonitorBehavior(
  timers: TimerScheduler[MonitorMessage],
  modelActor: ActorRef[ModelCommand],
  gossipActor: ActorRef[GossipCommand],
  rootActor: ActorRef[RootCommand],
  boundary: ViewBoundary,
  isMaster: Boolean
)(using appConfig: AppConfig):

  /**
   * Initial state: Waiting for Cluster/Config initialization.
   */
  def connecting(snapshot: ViewStateSnapshot): Behavior[MonitorMessage] =
    Behaviors.receive: (context, message) =>
      message match
        case MonitorCommand.Initialize(seed, model, config) =>
          context.log.info(s"Monitor: System Initialized. Seed: $seed")

          val newSnapshot = snapshot.copy(
            clusterSeed = Some(seed),
            model = Some(model),
            config = Some(config)
          )

          boundary.showInitialScreen(newSnapshot, isMaster)
          idle(newSnapshot)

        case MonitorCommand.ConnectionFailed(reason) =>
          context.log.info(s"Monitor: Critical connection failure: $reason")
          boundary.showInitialError(reason)
          Behaviors.stopped

        case MonitorCommand.PeerCountChanged(active, total) =>
          boundary.updatePeerDisplay(active, total)
          connecting(snapshot.copy(activePeers = active, totalPeers = total))

        case MonitorCommand.InternalStop =>
          context.log.info("Monitor: Remote STOP command.")
          boundary.stopSimulation()
          timers.cancelAll()
          Behaviors.stopped

        case _ => Behaviors.same

  /**
   * Idle state: Configured but simulation not started.
   */
  private def idle(snapshot: ViewStateSnapshot): Behavior[MonitorMessage] =
    Behaviors.receive: (context, message) =>
      message match
        case MonitorCommand.StartSimulation if isMaster =>
          context.log.info("Monitor: Master requested Simulation Start...")
          rootActor ! RootCommand.SeedStartSimulation
          active(snapshot)

        case MonitorCommand.StartWithData(trainSet, testSet) =>
          context.log.info(s"Monitor: Received subsection of ${trainSet.size + testSet.size} points. Start training...")

          val currentConfig = snapshot.config.getOrElse(throw new IllegalStateException("Config missing in idle"))
          val updatedConfig = currentConfig.copy(trainSet = trainSet, testSet = testSet)

          val newSnapshot = snapshot.copy(config = Some(updatedConfig))

          boundary.startSimulation(newSnapshot)
          timers.startTimerAtFixedRate(PrivateMonitorCommand.TickMetrics, appConfig.metricsInterval)

          gossipActor ! GossipCommand.StartGossipTick

          active(newSnapshot)

        case MonitorCommand.PeerCountChanged(active, total) =>
          context.log.info(s"Monitor: Cluster Status changed, $active/$total connected peer.")
          boundary.updatePeerDisplay(active, total)
          idle(snapshot.copy(activePeers = active, totalPeers = total))

        case MonitorCommand.InternalStop =>
          context.log.info("Monitor: Remote STOP command.")
          boundary.stopSimulation()
          timers.cancelAll()
          Behaviors.stopped

        case _ => Behaviors.unhandled

  /**
   * Active state: Simulation running, polling metrics.
   */
  private def active(snapshot: ViewStateSnapshot): Behavior[MonitorMessage] =
    Behaviors.receive: (context, message) =>
      message match
        case PrivateMonitorCommand.TickMetrics =>
          modelActor ! ModelCommand.GetMetrics(replyTo = context.self)
          Behaviors.same

        case MonitorCommand.ViewUpdateResponse(epoch, model, trainLoss, testLoss, consensus) =>
          context.log.info(s"Monitor Update - Train Loss: $trainLoss, Test Loss: $testLoss, Consensus Metric: $consensus")

          boundary.plotMetrics(epoch, trainLoss, testLoss, consensus)
          boundary.plotDecisionBoundary(model)

          /*snapshot.config.foreach { cfg =>
            if epoch >= cfg.epochs then
              context.log.info(s"Monitor: Target epochs (${cfg.epochs}) reached. Stopping Gossip Tick.")
              gossipActor ! GossipCommand.StopGossipTick
          }*/

          active(snapshot.copy(
            epoch = epoch,
            model = Some(model),
            trainLoss = Some(trainLoss),
            testLoss = Some(testLoss),
            consensus = Some(consensus),
          ))

        case MonitorCommand.PauseSimulation =>
          context.log.info("Monitor: User requested PAUSE. Propagating...")
          gossipActor ! GossipCommand.SpreadCommand(ControlCommand.GlobalPause)
          Behaviors.same

        case MonitorCommand.InternalPause =>
          context.log.info("Monitor: Remote PAUSE command.")
          boundary.setPausedState(true)
          paused(snapshot)

        case MonitorCommand.StopSimulation =>
          context.log.info("Monitor: User requested RESUME. Propagating...")
          gossipActor ! GossipCommand.SpreadCommand(ControlCommand.GlobalStop)
          Behaviors.same

        case MonitorCommand.InternalStop =>
          context.log.info("Monitor: Remote STOP command.")
          boundary.stopSimulation()
          timers.cancelAll()
          idle(snapshot)

        case MonitorCommand.PeerCountChanged(activePeers, totalPeers) =>
          boundary.updatePeerDisplay(activePeers, totalPeers)
          active(snapshot.copy(activePeers = activePeers, totalPeers = totalPeers))

        case MonitorCommand.RequestWeightsLog =>
          modelActor ! ModelCommand.ExportToFile()
          Behaviors.same

        case MonitorCommand.SimulateCrash =>
          context.log.warn("Monitor: Crash simulation. Forced node shutdown.")
          boundary.showCrashMessage()
          context.system.terminate()
          Behaviors.stopped

        case _ => Behaviors.unhandled

  /**
   * Paused state.
   */
  private def paused(snapshot: ViewStateSnapshot): Behavior[MonitorMessage] =
    Behaviors.receive: (context, message) =>
      message match
        case MonitorCommand.ResumeSimulation =>
          context.log.info("Monitor: User requested RESUME. Propagating...")
          gossipActor ! GossipCommand.SpreadCommand(ControlCommand.GlobalPause)
          Behaviors.same

        case MonitorCommand.InternalResume =>
          context.log.info("Monitor: Remote RESUME command.")
          boundary.setPausedState(false)
          timers.startTimerAtFixedRate(PrivateMonitorCommand.TickMetrics, appConfig.metricsInterval)
          active(snapshot)

        case MonitorCommand.StopSimulation =>
          context.log.info("Monitor: User requested STOP. Propagating...")
          gossipActor ! GossipCommand.SpreadCommand(ControlCommand.GlobalStop)
          Behaviors.same

        case MonitorCommand.InternalStop =>
          context.log.info("Monitor: Remote STOP command.")
          boundary.stopSimulation()
          timers.cancelAll()
          Behaviors.stopped

        case MonitorCommand.PeerCountChanged(active, total) =>
          boundary.updatePeerDisplay(active, total)
          paused(snapshot.copy(activePeers = active, totalPeers = total))

        case _ => Behaviors.same
