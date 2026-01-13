package actors

import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.typed.{Cluster, Subscribe}
import akka.cluster.ClusterEvent.{MemberEvent, MemberRemoved, MemberUp}
import com.typesafe.config.{Config, ConfigFactory}
import domain.network.Network
import domain.training.Strategies.Optimizers

object ClusterManager:

  private def rootBehavior(): Behavior[MemberEvent] = Behaviors.setup:
    context =>
      val cluster = Cluster(context.system)

      cluster.subscriptions ! Subscribe(context.self, classOf[MemberEvent])
      

      //TODO initialNetwork, optimizer da ModelActor
      
      val modelActor = context.spawn(ModelActor(initialNetwork, optimizer), "model-actor")

      val gossipActor = context.spawn(GossipActor(modelActor), "gossip-actor")

      val trainerActor = context.spawn(TrainerActor(modelActor), "trainer-actor")

      context.log.info(s"Nodo ${cluster.selfMember.address} avviato correttamente.")

      Behaviors.receive: (context, message) =>
        message match
          case MemberUp(member) =>
            context.log.info(s"--- NUOVO NODO UP: ${member.address} ---")
            context.log.info(s"Membri attuali nel cluster: ${cluster.state.members.mkString(", ")}")
            Behaviors.same
          case MemberRemoved(member, previousStatus) =>
            context.log.info(s"--- NODO RIMOSSO: ${member.address} (precedente stato: $previousStatus) ---")
            Behaviors.same
          case _ =>
            Behaviors.same

  @main def run(args: String*): Unit =
    args.toList match
      case "seed" :: port :: Nil =>
        startNode(port.toInt, s"127.0.0.1:$port")

      case "client" :: port :: seedHost :: seedPort :: Nil =>
        startNode(port.toInt, s"$seedHost:$seedPort")

      case _ =>
        println(
          """
            |Errore nei parametri!
            |Uso Seed:   run seed <port>
            |Uso Client: run client <port> <seedHost> <seedPort>
            |""".stripMargin)

  private def startNode(localPort: Int, seedAddress: String): Unit =
    val config = ConfigFactory.parseString(s"""
      akka {
        actor.provider = "cluster"
        remote.artery {
          canonical.hostname = "127.0.0.1"
          canonical.port = $localPort
        }
        cluster {
          seed-nodes = ["akka://ClusterSystem@$seedAddress"]
          downing-provider-class = "akka.cluster.sbr.SplitBrainResolverProvider"
        }
      }
    """).withFallback(ConfigFactory.load())

    ActorSystem(rootBehavior(), "ClusterSystem", config)