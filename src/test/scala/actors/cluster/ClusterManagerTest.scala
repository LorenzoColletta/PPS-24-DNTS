package actors.cluster

import akka.actor.testkit.typed.scaladsl.*
import akka.actor.typed.ActorSystem
import akka.cluster.typed.Cluster
import akka.cluster.ClusterEvent.*
import org.scalatest.funsuite.AnyFunSuite
import actors.cluster.*
import actors.monitor.MonitorProtocol.MonitorCommand
import actors.monitor.MonitorProtocol.MonitorCommand.PeerCountChanged
import actors.GossipActor.GossipCommand
import com.typesafe.config.ConfigFactory



class ClusterManagerTest extends AnyFunSuite :
  private val clusterConfig = ConfigFactory.parseString(
    """
    akka {
      actor.provider = "cluster"

      remote.artery {
        canonical.hostname = "127.0.0.1"
        canonical.port = 0
      }

      cluster {
        seed-nodes = []
        downing-provider-class = "akka.cluster.sbr.SplitBrainResolverProvider"
      }
    }
    """)
  private val testKit = ActorTestKit(clusterConfig)

  test("node joining cluster updates membership and notifies monitor"):

    val monitorProbe = testKit.createTestProbe[MonitorCommand]()
    val gossipProbe  = testKit.createTestProbe[GossipCommand]()

    testKit.spawn(
      ClusterManager(
        monitor = monitorProbe.ref,
        gossip  = gossipProbe.ref
      )
    )

    val cluster = Cluster(testKit.system)

    cluster.manager ! akka.cluster.typed.Join(cluster.selfMember.address)

    monitorProbe.awaitAssert {
      monitorProbe.expectMessage(PeerCountChanged(1, 1))
    }

    testKit.shutdownTestKit()

