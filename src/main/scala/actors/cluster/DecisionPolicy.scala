package actors.cluster

import actors.cluster.ClusterProtocol.*
import actors.cluster.effect.*
import actors.cluster.timer.{BootstrapTimerId, UnreachableTimerId}
import actors.cluster.{ClusterState, Joining, Running}
import actors.monitor.MonitorProtocol.MonitorCommand.PeerCountChanged
import actors.root.RootProtocol.NodeRole
import actors.root.RootProtocol.RootCommand.{ClusterFailed, ClusterReady}

sealed trait DecisionPolicy {
  def decide(state: ClusterState, msg: ClusterMemberCommand): List[Effect]
}

object BootstrapPolicy extends DecisionPolicy :

  def decide(state: ClusterState, message: ClusterMemberCommand): List[Effect] =
    def checkClusterConnection(state: ClusterState) = state.view.master.isDefined
    val joiningEffects = JoiningPolicy.decide(state, message)

    message match
      case JoinTimeout if checkClusterConnection(state) =>
          List(CancelTimer(BootstrapTimerId), NotifyRoot(ClusterReady), ChangePhase(Joining))

      case JoinTimeout =>
          List(NotifyRoot(ClusterFailed), StopBehavior)

      case _: NodeEvent =>
        if (checkClusterConnection(state))
          joiningEffects ++ List(NotifyRoot(ClusterReady), ChangePhase(Joining))
        else
          joiningEffects

      case _: AppClusterCommand =>
        List(NotifyRoot(InvalidCommandInBootstrap))


object JoiningPolicy extends DecisionPolicy :

  def decide(state: ClusterState, message: ClusterMemberCommand): List[Effect] =
    message match
      case NodeUp(node) =>
        List(
          NotifyMonitor,
          NotifyReceptionist(node.member.address)
        )

      case NodeUnreachable(node) if node.member.roles.contains(NodeRole.Seed.id) =>
        List(
          NotifyRoot(SeedLost),
          StopBehavior
        )

      case NodeUnreachable(node) =>
        List(
          RemoveNodeFromCluster(node.member.address),
          RemoveNodeFromMembership(node.member.address),
          NotifyMonitor,
          NotifyReceptionist(NodeUnavailable(node.member.address)),
          DownNode(node.member.address)
        )

      case StartSimulation =>
        List(ChangePhase(Running))

      case StopSimulation =>
        List(NotifyRoot(InvalidCommandInJoining))

      case _ => Nil


object RunningPolicy extends DecisionPolicy :

  def decide(state: ClusterState, message: ClusterMemberCommand): List[Effect] =
    message match

      case NodeUp(node) =>
        List(RemoveNodeFromCluster(node.member.address), RemoveNodeFromMembership(node.member.address))

      case NodeUnreachable(node) if node.member.roles.contains(NodeRole.Seed.id) =>
        List(
          StartTimer(UnreachableTimerId(node.member.address), SeedUnreachableTimeout),
          NotifyMonitor,
          NotifyReceptionist()
        )

      case NodeUnreachable(node) =>
        List(
          StartTimer(UnreachableTimerId(node.member.address), UnreachableTimeout(node.member.address)),
          NotifyMonitor,
          NotifyReceptionist()
        )

      case NodeReachable(node) =>
        List(
          CancelTimer(UnreachableTimerId(node.member.address)),
          NotifyMonitor,
          NotifyReceptionist()
        )

      case StopSimulation =>
        List(LeaveCluster)

      case SeedUnreachableTimeout =>
        List(NotifyRoot(), StopBehavior)

      case UnreachableTimeout(address) =>
        List(RemoveNodeFromMembership(address), DownNode(address))

      case _ =>
        Nil

