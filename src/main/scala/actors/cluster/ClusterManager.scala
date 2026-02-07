package actors.cluster

import actors.cluster.ClusterProtocol.*
import actors.cluster.effect.*
import actors.cluster.timer.*
import actors.cluster.*
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

      val adapterMemberUp: ActorRef[ClusterEvent.MemberUp] = context.messageAdapter(NodeUp.apply)
      val adapterMemberRemoved: ActorRef[ClusterEvent.MemberRemoved] = context.messageAdapter(NodeRemoved.apply)
      val adapterMemberReachable: ActorRef[ClusterEvent.ReachableMember] = context.messageAdapter(NodeReachable .apply)
      val adapterMemberUnreachable: ActorRef[ClusterEvent.UnreachableMember] = context.messageAdapter(NodeUnreachable.apply)

      cluster.subscriptions ! Subscribe(adapterMemberUp, classOf[ClusterEvent.MemberUp])
      cluster.subscriptions ! Subscribe(adapterMemberRemoved, classOf[ClusterEvent.MemberRemoved])
      cluster.subscriptions ! Subscribe(adapterMemberReachable, classOf[ClusterEvent.ReachableMember]      )
      cluster.subscriptions ! Subscribe(adapterMemberUnreachable, classOf[ClusterEvent.UnreachableMember])

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
          .foreach(effect => ClusterEffects(state, context, timers, effect, timersDuration, monitorActor,
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
