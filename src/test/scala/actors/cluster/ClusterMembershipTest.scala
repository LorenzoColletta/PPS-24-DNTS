package actors.cluster

import actors.cluster.membership.ClusterMembership
import akka.actor.Address
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ClusterMembershipTest extends AnyFunSuite with Matchers:

  private def address(id: Int): Address =
    Address("akka", "ClusterSystem", s"host$id", 2550 + id)

  test("empty membership has no nodes"):
    val m = ClusterMembership.empty

    m.nodesUp shouldBe empty
    m.nodesUnreachable shouldBe empty
    m.total shouldBe 0
    m.available shouldBe 0

  test("addNode adds a peer to the membership"):
    val a = address(1)

    val m = ClusterMembership.empty.addNode(a)

    m.nodesUp shouldBe Set(a)
    m.nodesUnreachable shouldBe empty
    m.total shouldBe 1
    m.available shouldBe 1

  test("addNode is idempotent"):
    val a = address(1)

    val m1 = ClusterMembership.empty.addNode(a)
    val m2 = m1.addNode(a)

    m2 shouldBe m1

  test("removeNode removes a peer from nodes and unreachable"):
    val a = address(1)

    val m = ClusterMembership.empty
      .addNode(a)
      .markUnreachable(a)
      .removeNode(a)

    m.nodesUp shouldBe empty
    m.nodesUnreachable shouldBe empty
    m.total shouldBe 0

  test("markUnreachable marks a peer as unreachable"):
    val a = address(1)

    val m = ClusterMembership.empty
      .addNode(a)
      .markUnreachable(a)

    m.nodesUnreachable shouldBe Set(a)
    m.available shouldBe 0

  test("markUnreachable is idempotent"):
    val a = address(1)

    val m1 = ClusterMembership.empty
      .addNode(a)
      .markUnreachable(a)

    val m2 = m1.markUnreachable(a)

    m2 shouldBe m1

  test("markReachable removes a peer from unreachable"):
    val a = address(1)

    val m = ClusterMembership.empty
      .addNode(a)
      .markUnreachable(a)
      .markReachable(a)

    m.nodesUnreachable shouldBe empty
    m.available shouldBe 1

  test("markReachable is idempotent"):
    val a = address(1)

    val m1 = ClusterMembership.empty.addNode(a)
    val m2 = m1.markReachable(a)

    m2 shouldBe m1

  test("unreachable nodes are always a subset of nodes"):
    val a = address(1)

    val m = ClusterMembership.empty
      .addNode(a)
      .markUnreachable(a)
      .removeNode(a)

    m.nodesUnreachable shouldBe empty

  test("available nodes never exceed total nodes"):
    val a = address(1)
    val b = address(2)

    val m = ClusterMembership.empty
      .addNode(a)
      .addNode(b)
      .markUnreachable(a)

    m.available should be <= m.total
