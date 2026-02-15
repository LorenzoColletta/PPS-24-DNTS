package actors.cluster

import actors.cluster.ClusterProtocol.*
import actors.cluster.effect.*
import actors.cluster.timer.{BootstrapTimerId, UnreachableTimerId}
import actors.cluster.{ClusterState, Joining, Running}
import actors.discovery.DiscoveryProtocol.{NotifyAddNode, NotifyRemoveNode, RegisterGossipPermit}
import actors.monitor.MonitorProtocol.MonitorCommand.PeerCountChanged
import actors.root.RootProtocol.NodeRole
import actors.root.RootActor.RootCommand.{ClusterFailed, ClusterReady, InvalidCommandInBootstrap, InvalidCommandInJoining, SeedLost}
import actors.root.RootProtocol.RootCommand.SeedLost


/**
 * Defines the decision-making logic for the ClusterManager.
 *
 * A DecisionPolicy encapsulates how the cluster reacts to incoming
 * ClusterMemberCommand messages depending on the current cluster phase.
 *
 * The returned Effects describe the actions to be executed by the ClusterManager.
 */
sealed trait DecisionPolicy {
  def decide(state: ClusterState, msg: ClusterMemberCommand): List[Effect]
}

/**
 * Decision policy active during the bootstrap phase.
 *
 * In this phase the ClusterManager:
 *  - waits for the initial cluster connection
 *  - determines whether the node can successfully join the cluster
 *  - transitions to the Joining phase once a master node is detected
 *
 * Failure to establish a connection within the bootstrap timeout
 * results in cluster startup failure.
 */
object BootstrapPolicy extends DecisionPolicy :

  def decide(state: ClusterState, message: ClusterMemberCommand): List[Effect] =
    def checkClusterConnection(state: ClusterState) = state.view.master.isDefined
    val joiningEffects = JoiningPolicy.decide(state, message)

    message match
      case JoinTimeout if checkClusterConnection(state) =>
          List(CancelTimer(BootstrapTimerId), NotifyRoot(ClusterReady), NotifyReceptionist(RegisterGossipPermit), ChangePhase
            (Joining))

      case JoinTimeout =>
        List(NotifyRoot(ClusterFailed), StopBehavior)

      case _: NodeEvent =>
        if (checkClusterConnection(state))
          joiningEffects ++ List(NotifyRoot(ClusterReady), NotifyReceptionist(RegisterGossipPermit), ChangePhase(Joining))
        else
          joiningEffects

      case _ => Nil

/**
 * Decision policy active while application is waiting for nodes to join the cluster.
 *
 * In this phase the ClusterManager:
 *  - reacts to new members joining or becoming unreachable
 *  - updates cluster membership 
 *  - waits for the application to start the simulation
 *
 * Once the simulation starts, the policy transitions to Running.
 */
object JoiningPolicy extends DecisionPolicy :

  def decide(state: ClusterState, message: ClusterMemberCommand): List[Effect] =
    message match
      case NodeUp(node) =>
        List(
          NotifyMonitor,
          NotifyReceptionist(NotifyAddNode(node.address))
        )

      case NodeUnreachable(node) if node.roles.contains(NodeRole.Seed.id) =>
        List(
          NotifyRoot(ClusterFailed),
          StopBehavior
        )

      case NodeUnreachable(node) =>
        List(
          RemoveNodeFromCluster(node.address),
          RemoveNodeFromMembership(node.address),
          NotifyMonitor,
          NotifyReceptionist(NotifyRemoveNode(node.address)),
          DownNode(node.address)
        )

      case StartSimulation =>
        List(ChangePhase(Running))

      case _ => Nil

/**
 * Decision policy active during normal cluster operation.
 *
 * In this phase the ClusterManager:
 *  - monitors node reachability
 *  - handles node failures and recoveries
 *  - manages timers for unreachable members
 *  - coordinates cluster shutdown scenarios
 */
object RunningPolicy extends DecisionPolicy :

  def decide(state: ClusterState, message: ClusterMemberCommand): List[Effect] =
    message match

      case NodeUp(node) =>
        List(RemoveNodeFromCluster(node.address), RemoveNodeFromMembership(node.address))

      case NodeUnreachable(node) if node.roles.contains(NodeRole.Seed.id) =>
        List(
          StartTimer(UnreachableTimerId(node.address), SeedUnreachableTimeout),
          NotifyMonitor,
          NotifyReceptionist(NotifyRemoveNode(node.address))
        )

      case NodeUnreachable(node) =>
        List(
          StartTimer(UnreachableTimerId(node.address), UnreachableTimeout(node.address)),
          NotifyMonitor,
          NotifyReceptionist(NotifyRemoveNode(node.address))
        )

      case NodeReachable(node) =>
        List(
          CancelTimer(UnreachableTimerId(node.address)),
          NotifyMonitor,
          NotifyReceptionist(NotifyAddNode(node.address))
        )

      case StopSimulation =>
        List(LeaveCluster)

      case SeedUnreachableTimeout =>
        List(NotifyRoot(SeedLost), StopBehavior)

      case UnreachableTimeout(address) =>
        List(RemoveNodeFromMembership(address), DownNode(address))

      case _ =>
        Nil

