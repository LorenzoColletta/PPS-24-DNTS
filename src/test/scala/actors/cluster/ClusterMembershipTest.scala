package actors.cluster

import akka.actor.Address
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ClusterMembershipTest extends AnyFunSuite with Matchers {

  private def address(id: Int): Address =
    Address("akka", "ClusterSystem", s"host$id", 2550 + id)

  test("empty membership has no peers") {
    val m = ClusterMembership.empty

    m.peers shouldBe empty
    m.unreachable shouldBe empty
    m.total shouldBe 0
    m.available shouldBe 0
  }

  test("addPeer adds a peer to the membership") {
    val a = address(1)

    val m = ClusterMembership.empty.addPeer(a)

    m.peers shouldBe Set(a)
    m.unreachable shouldBe empty
    m.total shouldBe 1
    m.available shouldBe 1
  }

  test("addPeer is idempotent") {
    val a = address(1)

    val m1 = ClusterMembership.empty.addPeer(a)
    val m2 = m1.addPeer(a)

    m2 shouldBe m1
  }

  test("removePeer removes a peer from peers and unreachable") {
    val a = address(1)

    val m = ClusterMembership.empty
      .addPeer(a)
      .markUnreachable(a)
      .removePeer(a)

    m.peers shouldBe empty
    m.unreachable shouldBe empty
    m.total shouldBe 0
  }

  test("markUnreachable marks a peer as unreachable") {
    val a = address(1)

    val m = ClusterMembership.empty
      .addPeer(a)
      .markUnreachable(a)

    m.unreachable shouldBe Set(a)
    m.available shouldBe 0
  }

  test("markUnreachable is idempotent") {
    val a = address(1)

    val m1 = ClusterMembership.empty
      .addPeer(a)
      .markUnreachable(a)

    val m2 = m1.markUnreachable(a)

    m2 shouldBe m1
  }

  test("markReachable removes a peer from unreachable") {
    val a = address(1)

    val m = ClusterMembership.empty
      .addPeer(a)
      .markUnreachable(a)
      .markReachable(a)

    m.unreachable shouldBe empty
    m.available shouldBe 1
  }

  test("markReachable is idempotent") {
    val a = address(1)

    val m1 = ClusterMembership.empty.addPeer(a)
    val m2 = m1.markReachable(a)

    m2 shouldBe m1
  }

  test("unreachable peers are always a subset of peers") {
    val a = address(1)

    val m = ClusterMembership.empty
      .addPeer(a)
      .markUnreachable(a)
      .removePeer(a)

    m.unreachable shouldBe empty
  }

  test("available peers never exceed total peers") {
    val a = address(1)
    val b = address(2)

    val m = ClusterMembership.empty
      .addPeer(a)
      .addPeer(b)
      .markUnreachable(a)

    m.available should be <= m.total
  }
}
