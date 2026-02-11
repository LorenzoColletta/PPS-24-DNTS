package actors.cluster.adapter

import akka.cluster.ClusterEvent.{MemberRemoved, MemberUp, ReachableMember, UnreachableMember}
import akka.cluster.Member

/**
 * Typeclass describing cluster events that expose a [[akka.cluster.Member]].
 */
trait HasMember[E]:
  def member(e: E): Member

given HasMember[MemberUp] with
  def member(e: MemberUp): Member = e.member

given HasMember[MemberRemoved] with
  def member(e: MemberRemoved): Member = e.member

given HasMember[ReachableMember] with
  def member(e: ReachableMember): Member = e.member

given HasMember[UnreachableMember] with
  def member(e: UnreachableMember): Member = e.member
