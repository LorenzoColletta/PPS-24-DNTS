package actors.cluster.adapter

import actors.cluster.ClusterNode
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext

import scala.reflect.ClassTag

/**
 * Creates typed message adapters for Akka Cluster events.
 *
 * This adapter converts cluster events exposing a [[akka.cluster.Member]]
 * into domain-level commands based on [[ClusterNode]].
 */
object ClusterEventAdapter:

  def adapt[E: ClassTag, T](context: ActorContext[T])(
    f: ClusterNode => T
  )(using hm: HasMember[E]): ActorRef[E] =
    context.messageAdapter[E] { event =>
      val member = hm.member(event)
      f(ClusterNodeAdapter.fromMember(member))
    }
