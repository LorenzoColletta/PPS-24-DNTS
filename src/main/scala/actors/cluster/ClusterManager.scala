package actors.cluster

import actors.cluster.ClusterProtocol.*
import actors.cluster.effect.*
import actors.cluster.timer.*
import actors.cluster.*
import actors.cluster.adapter.ClusterEventAdapter
import actors.monitor.MonitorActor.MonitorCommand
import actors.monitor.MonitorActor.MonitorCommand.PeerCountChanged
import actors.gossip.GossipActor.GossipCommand
import actors.discovery.DiscoveryProtocol.DiscoveryCommand
import actors.root.RootProtocol.{NodeRole, RootCommand}
import akka.actor.typed.*
import akka.actor.typed.scaladsl.*
import akka.cluster.ClusterEvent.{MemberEvent, ReachabilityEvent}
import akka.cluster.ClusterEvent
import akka.cluster.typed.{Cluster, Down, Leave, Subscribe}
import actors.cluster.adapter.given
import actors.monitor.MonitorProtocol.MonitorCommand.PeerCountChanged

/**
 * Actor responsible to the management of the cluster.
 *
 * Depending on the current phase it manages:
 *  - the connection of a new node
 *  - the loss of connection with a known node
 */
object ClusterManager:

  /**
   * It creates the ClusterManager behavior.
   *
   * Initializes:
   *  - cluster event subscriptions
   *  - bootstrap timer
   *
   * @param initialState        the initial state
   * @param timersDuration      timers configuration
   * @param monitorActor        the monitor actor
   * @param receptionistManager the receptionist manager actor
   * @param rootActor           the root actor
   * @return the actor's behavior
   */
  def apply(
    initialState: ClusterState,
    timersDuration: ClusterTimers,
    monitorActor: Option[ActorRef[MonitorCommand]],
    receptionistManager: ActorRef[DiscoveryCommand],
    rootActor: ActorRef[RootCommand]
  ): Behavior[ClusterMemberCommand] =
    Behaviors.setup { context =>

      val cluster = Cluster(context.system)

      val nodeUpAdapter = ClusterEventAdapter.adapt[ClusterEvent.MemberUp, ClusterMemberCommand](context)(NodeUp.apply)
      val nodeRemovedAdapter = ClusterEventAdapter.adapt[ClusterEvent.MemberRemoved, ClusterMemberCommand](context)(NodeRemoved.apply)
      val nodeReachableAdapter = ClusterEventAdapter.adapt[ClusterEvent.ReachableMember, ClusterMemberCommand](context)(NodeReachable.apply)
      val nodeUnreachableAdapter = ClusterEventAdapter.adapt[ClusterEvent.UnreachableMember, ClusterMemberCommand](context)(NodeUnreachable.apply)

      cluster.subscriptions ! Subscribe(nodeUpAdapter, classOf[ClusterEvent.MemberUp])
      cluster.subscriptions ! Subscribe(nodeRemovedAdapter, classOf[ClusterEvent.MemberRemoved])
      cluster.subscriptions ! Subscribe(nodeReachableAdapter, classOf[ClusterEvent.ReachableMember]      )
      cluster.subscriptions ! Subscribe(nodeUnreachableAdapter, classOf[ClusterEvent.UnreachableMember])

      Behaviors.withTimers { timers =>
        timers.startSingleTimer(BootstrapTimerId, JoinTimeout, timersDuration.bootstrapCheck)
        runningBehavior(context, timers, initialState, timersDuration, monitorActor, receptionistManager, rootActor)
      }
    }

  private def runningBehavior(
    context: ActorContext[ClusterMemberCommand],
    timers: TimerScheduler[ClusterMemberCommand],
    state: ClusterState,
    timersDuration: ClusterTimers,
    monitorActor: Option[ActorRef[MonitorCommand]],
    receptionistManager: ActorRef[DiscoveryCommand],
    rootActor: ActorRef[RootCommand]
  ): Behavior[ClusterMemberCommand] =
    Behaviors.receiveMessage {
      case RegisterMonitor(ref) =>
        ref ! PeerCountChanged(state.view.available, state.view.total)
        runningBehavior(
          context,
          timers,
          state,
          timersDuration,
          Some(ref),
          receptionistManager,
          rootActor
        )

      case message =>

        val (stateAfterHandle, effects) = handle(state, message)

        val newState = effects.foldLeft(stateAfterHandle) {
          case (state, ChangePhase(to)) =>
            state.copy(phase = to)

          case (state, RemoveNodeFromMembership(nodeId)) =>
            state.copy(view = state.view.removeNode(nodeId))

          case (state, _) => state
        }

        effects.collect { case e: Action => e }
          .foreach(effect => ClusterEffects(newState, context, timers, effect, timersDuration, monitorActor,
            receptionistManager, rootActor))

        runningBehavior(context, timers, newState, timersDuration, monitorActor, receptionistManager, rootActor)
    }

  private def handle(
    state: ClusterState,
    message: ClusterMemberCommand
  ): (ClusterState, List[Effect]) = {

    val newView =
      message match
        case e: NodeEvent =>
          membership.MembershipPolicy.update(state.view, e)
        case _ => state.view

    val newState = state.copy(view = newView)

    val policy = state.phase match
      case Bootstrap => BootstrapPolicy
      case Joining   => JoiningPolicy
      case Running   => RunningPolicy


    val effects = policy.decide(newState, message)

    (newState, effects)
  }
