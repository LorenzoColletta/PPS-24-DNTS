package actors

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import akka.actor.testkit.typed.scaladsl.*
import akka.actor.typed.ActorRef
import akka.actor.Address
import actors.discovery.DiscoveryProtocol.*
import actors.discovery.{DiscoveryActor, GossipPeerState}
import actors.gossip.GossipActor.GossipCommand
import scala.concurrent.duration._

class DiscoveryActorTest extends AnyFunSuite with Matchers:

  private val address1 =
    Address("akka", "TestSystem", "node1", 2551)

  private val address2 = ("akka", "TestSystem", "node2", 2552)

  private def spawnDiscovery(state: GossipPeerState, testKit: ActorTestKit) =
    testKit.spawn(DiscoveryActor(state))

  private def withTestKit(testCode: ActorTestKit => Any): Unit =
    val testKit = ActorTestKit()
    try {
      testCode(testKit)
    } finally {
      testKit.shutdownTestKit()
    }


  test("returns empty list when no known references and no accepted nodes"):
    withTestKit { testKit =>
      val discovery = spawnDiscovery(GossipPeerState.empty, testKit)
      val replyProbe = testKit.createTestProbe[List[ActorRef[GossipCommand]]]()

      discovery ! NodesRefRequest(replyProbe.ref)

      replyProbe.expectMessage(Nil)

    }


  test("does not return ActorRef if address is accepted but ActorRef is unknown"):
    withTestKit { testKit =>
      val discovery = spawnDiscovery(GossipPeerState.empty, testKit)
      val replyProbe = testKit.createTestProbe[List[ActorRef[GossipCommand]]]()

      discovery ! NotifyAddNode(address1)
      discovery ! NodesRefRequest(replyProbe.ref)

      replyProbe.expectMessage(Nil)

    }

  test("does not return ActorRef if ActorRef is known but address is not accepted"):
    withTestKit { testKit =>
      val gossipProbe = testKit.createTestProbe[GossipCommand]()
      val ref = gossipProbe.ref

      val discovery = spawnDiscovery(GossipPeerState.empty, testKit)

      discovery ! RegisterGossip(ref)
      val replyProbe = testKit.createTestProbe[List[ActorRef[GossipCommand]]]()
      discovery ! NodesRefRequest(replyProbe.ref)
      replyProbe.expectMessage(3.seconds, List())

    }

  test("returns ActorRef only when both ActorRef is known and address is accepted"):
    withTestKit { testKit =>

      val gossipProbe = testKit.createTestProbe[GossipCommand]()
      val ref = gossipProbe.ref

      val discovery = spawnDiscovery(GossipPeerState.empty, testKit)

  //    val replyProbe = testKit.createTestProbe[List[ActorRef[GossipCommand]]]()

      discovery ! RegisterGossip(ref)
      discovery ! RegisterGossipPermit
      discovery ! NotifyAddNode(ref.path.address)
  //    discovery ! NodesRefRequest(replyProbe.ref)

  //    replyProbe.expectMessage(3.seconds, List(ref))
      val replyProbe = testKit.createTestProbe[List[ActorRef[GossipCommand]]]()
      gossipProbe.awaitAssert {
        discovery ! NodesRefRequest(replyProbe.ref)
        replyProbe.expectMessage(List(ref))
      }

    }

  test("removes ActorRef from acceptedReferences when node address is removed"):
    withTestKit { testKit =>

      val gossipProbe = testKit.createTestProbe[GossipCommand]()
      val ref = gossipProbe.ref


      val discovery =
        spawnDiscovery(
          GossipPeerState.empty
            .updateKnownReferences(Set(ref))
            .acceptNode(ref.path.address),
          testKit
        )

      val replyProbe = testKit.createTestProbe[List[ActorRef[GossipCommand]]]()

      discovery ! NotifyRemoveNode(ref.path.address)
      discovery ! NodesRefRequest(replyProbe.ref)

      replyProbe.expectMessage(Nil)

    }

