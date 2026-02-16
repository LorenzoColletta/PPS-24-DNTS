package actors.cluster

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import akka.actor.testkit.typed.scaladsl.*
import akka.actor.typed.ActorRef
import akka.actor.Address
import actors.cluster.ClusterProtocol.*
import actors.cluster.membership.ClusterMembership
import actors.cluster.timer.*
import actors.discovery.DiscoveryProtocol.*
import actors.monitor.MonitorActor.MonitorCommand
import actors.root.RootProtocol.*
import actors.root.RootProtocol.RootCommand.*
import com.typesafe.config.ConfigFactory

class ClusterManagerTest extends AnyFunSuite with Matchers:

  private val clusterConfig = ConfigFactory.parseString("""
    akka.actor.provider = "cluster"

    akka.remote.artery {
      canonical.hostname = "127.0.0.1"
      canonical.port = 0
    }

    akka.cluster.jmx.enabled = off
    akka.loglevel = "OFF"
  """)

  private val testKit = ActorTestKit(clusterConfig)

  private val address =
    Address("akka", "TestSystem", "localhost", 2552)

  private val seedNode =
    ClusterNode(address, Set(NodeRole.Seed.id))

  private val workerNode =
    ClusterNode(address, Set("worker"))

  private val timers =
    ClusterTimers(
      bootstrapCheck  = scala.concurrent.duration.DurationInt(1).second,
      unreachableNode     = scala.concurrent.duration.DurationInt(1).second
    )

  private def spawnManager(
    phase: Phase,
    monitor: Option[ActorRef[MonitorCommand]],
    receptionist: ActorRef[DiscoveryCommand],
    root: ActorRef[RootCommand]
  ): ActorRef[ClusterMemberCommand] =
    testKit.spawn(
      ClusterManager(
        initialState = ClusterState(
          phase = phase,
          NodeRole.Seed,
          view  = ClusterMembership.empty
        ),
        timersDuration = timers,
        monitorActor = monitor,
        receptionistManager = receptionist,
        rootActor = root
      )
    )

  test("ClusterManager notifies receptionist and monitor on NodeUp during Joining"):
    val monitorProbe      = testKit.createTestProbe[MonitorCommand]()
    val receptionistProbe = testKit.createTestProbe[DiscoveryCommand]()
    val rootProbe         = testKit.createTestProbe[RootCommand]()

    val manager =
      spawnManager(
        Joining,
        Some(monitorProbe.ref),
        receptionistProbe.ref,
        rootProbe.ref
      )

    manager ! NodeUp(workerNode)

    receptionistProbe.expectMessage(
      NotifyAddNode(workerNode.address)
    )

    monitorProbe.expectMessageType[MonitorCommand]
    rootProbe.expectNoMessage()

  test("ClusterManager fails cluster when seed becomes unreachable during Joining"):
    val rootProbe = testKit.createTestProbe[RootCommand]()

    val manager =
      spawnManager(
        Joining,
        monitor = None,
        receptionist = testKit.createTestProbe[DiscoveryCommand]().ref,
        root = rootProbe.ref
      )

    manager ! NodeUnreachable(seedNode)

    rootProbe.expectMessage(ClusterFailed)

  test("ClusterManager downs node and notifies receptionist when non-seed becomes unreachable during Joining"):
    val receptionistProbe = testKit.createTestProbe[DiscoveryCommand]()

    val manager =
      spawnManager(
        Joining,
        monitor = None,
        receptionist = receptionistProbe.ref,
        root = testKit.createTestProbe[RootCommand]().ref
      )

    manager ! NodeUnreachable(workerNode)

    receptionistProbe.expectMessage(
      NotifyRemoveNode(workerNode.address)
    )

  test("ClusterManager leaves cluster on StopSimulation during Running"):
    val manager =
      spawnManager(
        Running,
        monitor = None,
        receptionist = testKit.createTestProbe[DiscoveryCommand]().ref,
        root = testKit.createTestProbe[RootCommand]().ref
      )

    manager ! StopSimulation

    testKit.stop(manager)

  test("ClusterManager transitions to Running on StartSimulation during Joining"):
    val manager =
      spawnManager(
        Joining,
        monitor = None,
        receptionist = testKit.createTestProbe[DiscoveryCommand]().ref,
        root = testKit.createTestProbe[RootCommand]().ref
      )

    manager ! StartSimulation

    testKit.stop(manager)
