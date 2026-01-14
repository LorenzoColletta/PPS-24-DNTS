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
    case StartWithData(config: TrainingConfig, dataSlice: List[LabeledPoint2D])
    case StopSimulation
    case PauseSimulation
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
        case MonitorCommand.StartSimulation(config) =>
          context.log.info("Monitor: Starting configured simulation...")

          // TODO: In una implementazione reale, qui si chiamerebbe il DatasetGenerator
          //       e si invierebbero i messaggi StartWithData tramite il GossipActor
          ga ! GossipCommand.SpreadCommand(GossipCommand.GlobalStart(config))

          timers.startTimerAtFixedRate(MonitorCommand.TickMetrics, MetricsInterval)
          active(ma, ta, ga, timers, state)

        case MonitorCommand.StartWithData(config, slice) =>
          context.log.info(s"Monitor: Received subsection of ${slice.size} points. Start training...")
          
          ta ! TrainerCommand.Start(config.copy(dataset = slice))
          
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
          ta ! TrainerCommand.Pause
          ga ! GossipCommand.SpreadCommand(GossipCommand.GlobalPause)
          Behaviors.same

        case MonitorCommand.SimulateCrash =>
          context.log.warn("Monitor: Crash simulation. Forced node shutdown.")

          // TODO: GUI.showCrashMessage()
          context.system.terminate()
          
          Behaviors.stopped

        case MonitorCommand.StopSimulation =>
          context.log.info("Monitor: Stopping the simulation.")

          ta ! TrainerCommand.Stop

          timers.cancelAll()
          waiting(ma, ta, ga, timers, state)

        case _ => Behaviors.unhandled
