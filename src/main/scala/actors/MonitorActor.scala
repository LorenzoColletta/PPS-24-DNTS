package actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import scala.concurrent.duration.*

import actors.TrainerActor.{TrainingConfig, TrainerCommand}
import actors.ModelActor.ModelCommand

object MonitorActor:

  enum MonitorCommand:
    case StartSimulation(config: TrainingConfig)
    case StopSimulation
    case TickMetrics
    case MetricsResponse(loss: Double, consensus: Double)


  private final val MetricsInterval = 500.millis

  def apply(modelActor: ActorRef[ModelCommand], trainerActor: ActorRef[TrainerCommand]): Behavior[MonitorCommand] =
    Behaviors.setup: context =>
      Behaviors.withTimers: timers =>
        context.log.info("Monitor: Initialized and waiting for user commands.")
        waiting(modelActor, trainerActor, timers)

  private def waiting(
    ma: ActorRef[ModelCommand],
    ta: ActorRef[TrainerCommand],
    timers: TimerScheduler[MonitorCommand]
  ): Behavior[MonitorCommand] =

    Behaviors.receive: (context, message) =>
      message match
        case MonitorCommand.StartSimulation(config) =>
          context.log.info("Monitor: Starting configured simulation...")

          ta ! TrainerCommand.Start(config)

          timers.startTimerAtFixedRate(MonitorCommand.TickMetrics, MetricsInterval)
          active(ma, ta, timers)

        case _ => Behaviors.unhandled

  private def active(
    ma: ActorRef[ModelCommand],
    ta: ActorRef[TrainerCommand],
    timers: TimerScheduler[MonitorCommand]
  ): Behavior[MonitorCommand] =

    Behaviors.receive: (context, message) =>
      message match
        case MonitorCommand.TickMetrics =>
          ma ! ModelCommand.GetMetrics(replyTo = context.self)
          Behaviors.same

        case MonitorCommand.MetricsResponse(loss, consensus) =>
          context.log.info(s"Monitor Update - Loss: $loss, Consensus Metric: $consensus")
          // TODO: gui update
          Behaviors.same

        case MonitorCommand.StopSimulation =>
          context.log.info("Monitor: Stopping the simulation.")

          ta ! TrainerCommand.Stop

          timers.cancelAll()
          waiting(ma, ta, timers)

        case _ => Behaviors.unhandled
