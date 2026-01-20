package actors.cluster

import akka.actor.typed.*
import akka.actor.typed.scaladsl.*
import akka.cluster.ClusterEvent.{MemberEvent, ReachabilityEvent}
import actors.monitor.MonitorProtocol.MonitorCommand
import actors.GossipActor.GossipCommand
import akka.cluster.typed.{Cluster, Subscribe}

/**
 * Entry point actor for listening to Akka Cluster events.
 *
 * @param monitor
 * Actor reference to the monitoring actor that tracks peer counts.
 * @param gossip
 * Actor reference to the gossip actor that propagates membership changes to peers.
 * @return
 * A [[Behavior]] that manages cluster subscriptions and delegates events to
 * [[ClusterBehaviors.connecting]].
 */
object ClusterManager:

  def apply(
    monitor: ActorRef[MonitorCommand],
    gossip: ActorRef[GossipCommand]
  ): Behavior[ClusterCommand] =
    Behaviors.setup { context =>

      val memberEventAdapter: ActorRef[MemberEvent] = context.messageAdapter(MemberCommand.apply)
      Cluster(context.system).subscriptions ! Subscribe(memberEventAdapter, classOf[MemberEvent])

      val reachabilityEventAdapter: ActorRef[ReachabilityEvent] = context.messageAdapter(ReachabilityCommand.apply)
      Cluster(context.system).subscriptions ! Subscribe(reachabilityEventAdapter, classOf[ReachabilityEvent])

      ClusterBehaviors.connecting(
        context,
        ClusterMembership.empty,
        monitor,
        gossip
      )
    }
