package actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}

import scala.concurrent.duration.*
import actors.TrainerActor.{TrainerCommand, TrainingConfig}
import actors.ModelActor.ModelCommand
import actors.GossipActor.GossipCommand
import domain.data.LabeledPoint2D

object MonitorActor:

  enum MonitorCommand:
    case StartSimulation(config: TrainingConfig)
    case StartWithData(config: TrainingConfig)
    case StopSimulation
    case PauseSimulation
    case ResumeSimulation
    case TickMetrics
    case MetricsResponse(loss: Double, consensus: Double)
    case SimulateCrash
    case PeerCountChanged(active: Int, total: Int)

  private case class MonitorState(
    peerCount: Int = 1,
    totalPeersDiscovered: Int = 1,
    isMaster: Boolean = false
  )


  private final val MetricsInterval = 500.millis

  def apply(
    modelActor: ActorRef[ModelCommand],
    trainerActor: ActorRef[TrainerCommand],
    gossipActor: ActorRef[GossipCommand],
    isMaster: Boolean = false
  ): Behavior[MonitorCommand] =

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
  ): Behavior[MonitorCommand] =

    Behaviors.receive: (context, message) =>
      message match
        case MonitorCommand.StartSimulation(config) if state.isMaster =>
          context.log.info("Monitor: Starting configured simulation...")

          // val fullDataset = DatasetGenerator.generate(...)
          // val allSlices = DataSplitter.split(fullDataset, state.peerCount)

          ga ! GossipCommand.DistributeData(allSlices.map(s => config.copy(dataset = s)))

          active(ma, ta, ga, timers, state)

        case MonitorCommand.StartWithData(config) =>
          context.log.info(s"Monitor: Received subsection of ${config.dataset.size} points. Start training...")

          ta ! TrainerCommand.Start(config)

          timers.startTimerAtFixedRate(MonitorCommand.TickMetrics, MetricsInterval)
          
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
  ): Behavior[MonitorCommand] =

    Behaviors.receive: (context, message) =>
      message match
        case MonitorCommand.TickMetrics =>
          ma ! ModelCommand.GetMetrics(replyTo = context.self)
          Behaviors.same

        case MonitorCommand.MetricsResponse(loss, consensus) =>
          context.log.info(s"Monitor Update - Loss: $loss, Consensus Metric: $consensus")

          // TODO: GUI.plot(loss, consensus)
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
