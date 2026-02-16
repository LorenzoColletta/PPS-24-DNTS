package actors.cluster

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import akka.actor.Address
import actors.cluster.effect.*
import actors.cluster.timer.*
import actors.discovery.DiscoveryProtocol.*
import actors.root.RootProtocol.*
import actors.root.RootProtocol.RootCommand.*
import actors.cluster.ClusterProtocol.*
import actors.cluster.membership.ClusterMembership

class DecisionPolicyTest extends AnyFunSuite with Matchers:

  private val address =
    Address("akka", "TestSystem", "localhost", 2552)

  private val seedNode =
    ClusterNode(address, Set(NodeRole.Seed.id))

  private val workerNode =
    ClusterNode(address, Set("worker"))

  private def baseState(
    phase: Phase,
    hasMaster: Boolean = false
  ): ClusterState =
    ClusterState(
      phase = phase,
      NodeRole.Seed,
      view  =
        if hasMaster then
          ClusterMembership.empty.addNode(address).setMaster(address)
        else
          ClusterMembership.empty
    )


  test("BootstrapPolicy: JoinTimeout with master -> success"):
    val state = baseState(Bootstrap, hasMaster = true)

    BootstrapPolicy.decide(state, JoinTimeout) should contain allOf (
      CancelTimer(BootstrapTimerId),
      NotifyRoot(ClusterReady),
      ChangePhase(Joining)
    )

  test("BootstrapPolicy: JoinTimeout without master -> failure"):
    val state = baseState(Bootstrap, hasMaster = false)

    BootstrapPolicy.decide(state, JoinTimeout) should contain allOf (
      NotifyRoot(ClusterFailed),
      StopBehavior
    )

  test("BootstrapPolicy: NodeEvent without master -> no phase change"):
    val state = baseState(Bootstrap, hasMaster = false)

    val effects =
      BootstrapPolicy.decide(state, NodeUp(workerNode))

    effects should not contain ChangePhase(Joining)

  test("BootstrapPolicy: NodeEvent with master -> transition to Joining"):
    val state = baseState(Bootstrap, hasMaster = true)

    BootstrapPolicy.decide(state, NodeUp(workerNode)) should contain allOf (
      NotifyRoot(ClusterReady),
      ChangePhase(Joining)
    )

  test("JoiningPolicy: NodeUp -> notify monitor and receptionist"):
    val state = baseState(Joining)

    JoiningPolicy.decide(state, NodeUp(workerNode)) should contain allOf (
      NotifyMonitor,
      NotifyReceptionist(NotifyAddNode(workerNode.address))
    )

  test("JoiningPolicy: NodeUnreachable seed -> cluster failure"):
    val state = baseState(Joining)

    JoiningPolicy.decide(state, NodeUnreachable(seedNode)) should contain allOf (
      NotifyRoot(ClusterFailed),
      StopBehavior
    )

  test("JoiningPolicy: NodeUnreachable non-seed -> remove and down"):
    val state = baseState(Joining)

    JoiningPolicy.decide(state, NodeUnreachable(workerNode)) should contain allOf (
      RemoveNodeFromCluster(workerNode.address),
      RemoveNodeFromMembership(workerNode.address),
      NotifyMonitor,
      NotifyReceptionist(NotifyRemoveNode(workerNode.address)),
      DownNode(workerNode.address)
    )

  test("JoiningPolicy: StartSimulation -> transition to Running"):
    val state = baseState(Joining)

    JoiningPolicy.decide(state, StartSimulation) should contain (
      ChangePhase(Running)
    )

  test("RunningPolicy: NodeUp -> removes it"):
    val state = baseState(Running)

    RunningPolicy.decide(state, NodeUp(workerNode)) should contain allOf (
      RemoveNodeFromCluster(workerNode.address),
      RemoveNodeFromMembership(workerNode.address)
    )

  test("RunningPolicy: NodeUnreachable seed -> start seed timer"):
    val state = baseState(Running)

    RunningPolicy.decide(state, NodeUnreachable(seedNode)) should contain allOf (
      StartTimer(UnreachableTimerId(seedNode.address), SeedUnreachableTimeout),
      NotifyMonitor,
      NotifyReceptionist(NotifyRemoveNode(seedNode.address))
    )

  test("RunningPolicy: NodeUnreachable non-seed -> start unreachable timer"):
    val state = baseState(Running)

    RunningPolicy.decide(state, NodeUnreachable(workerNode)) should contain allOf (
      StartTimer(UnreachableTimerId(workerNode.address), UnreachableTimeout(workerNode.address)),
      NotifyMonitor,
      NotifyReceptionist(NotifyRemoveNode(workerNode.address))
    )

  test("RunningPolicy: NodeReachable -> cancel timer and notify"):
    val state = baseState(Running)

    RunningPolicy.decide(state, NodeReachable(workerNode)) should contain allOf (
      CancelTimer(UnreachableTimerId(workerNode.address)),
      NotifyMonitor,
      NotifyReceptionist(NotifyAddNode(workerNode.address))
    )

  test("RunningPolicy: StopSimulation -> leave cluster"):
    val state = baseState(Running)

    RunningPolicy.decide(state, StopSimulation) should contain (
      LeaveCluster
    )

  test("RunningPolicy: SeedUnreachableTimeout -> cluster failure"):
    val state = baseState(Running)

    RunningPolicy.decide(state, SeedUnreachableTimeout) should contain allOf (
      NotifyRoot(SeedLost),
      StopBehavior
    )

  test("RunningPolicy: UnreachableTimeout -> remove and down node"):
    val state = baseState(Running)

    RunningPolicy.decide(state, UnreachableTimeout(workerNode.address)) should contain allOf (
      RemoveNodeFromMembership(workerNode.address),
      DownNode(workerNode.address)
    )
