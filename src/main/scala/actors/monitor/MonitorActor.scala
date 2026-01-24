package actors.monitor

import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import config.AppConfig
import actors.gossip.GossipActor.GossipCommand
import actors.model.ModelActor.ModelCommand
import actors.root.RootActor.RootCommand
import actors.trainer.TrainerActor.TrainerCommand
import view.{ViewBoundary, ViewStateSnapshot}

/**
 * Supervisor actor for the simulation lifecycle on a single node.
 * Acts as the main controller for the UI.
 */
object MonitorActor:

  export MonitorProtocol.*

  /**
   * Creates the initial behavior for the MonitorActor.
   *
   * @param modelActor   Reference to the local ModelActor.
   * @param trainerActor Reference to the local TrainerActor.
   * @param gossipActor  Reference to the local GossipActor.
   * @param rootActor    Reference to the local RootActor.
   * @param boundary     The abstraction acting as a bridge to the View.
   * @param isMaster     If true, this node orchestrates the simulation start.
   * @param appConfig    Implicit application global configuration.
   */
  def apply(
             modelActor: ActorRef[ModelCommand],
             trainerActor: ActorRef[TrainerCommand],
             gossipActor: ActorRef[GossipCommand],
             rootActor: ActorRef[RootCommand],
             boundary: ViewBoundary,
             isMaster: Boolean = false
           )(using appConfig: AppConfig): Behavior[MonitorMessage] =

    Behaviors.setup: context =>
      Behaviors.withTimers: timers =>
        new MonitorBehavior(
          context,
          timers,
          modelActor,
          trainerActor,
          gossipActor,
          rootActor,
          boundary,
          isMaster
        ).connecting(ViewStateSnapshot())