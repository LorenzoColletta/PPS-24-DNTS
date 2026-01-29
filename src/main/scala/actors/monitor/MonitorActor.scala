package actors.monitor

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import config.AppConfig
import actors.gossip.GossipActor.GossipCommand
import actors.model.ModelActor.ModelCommand
import actors.root.RootActor.RootCommand
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
   * @param gossipActor  Reference to the local GossipActor.
   * @param rootActor    Reference to the local RootActor.
   * @param boundary     The abstraction acting as a bridge to the View.
   * @param isMaster     If true, this node orchestrates the simulation start.
   * @param appConfig    Implicit application global configuration.
   */
  def apply(
    modelActor: ActorRef[ModelCommand],
    gossipActor: ActorRef[GossipCommand],
    rootActor: ActorRef[RootCommand],
    boundary: ViewBoundary,
    isMaster: Boolean = false
  )(using appConfig: AppConfig): Behavior[MonitorMessage] =

    Behaviors.setup: context =>

      boundary.bindController(cmd => context.self ! cmd)

      Behaviors.withTimers: timers =>
        new MonitorBehavior(
          timers,
          modelActor,
          gossipActor,
          rootActor,
          boundary, 
          isMaster
        ).connecting(ViewStateSnapshot())
