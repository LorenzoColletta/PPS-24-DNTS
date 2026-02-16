//import actors.cluster.{ClusterProtocol, ClusterManager}
//import akka.actor.typed.ActorSystem
//import com.typesafe.config.ConfigFactory
//
//object Main extends App {
//
//  val port = args(1).toInt
//  val seedNodes = if (args.length >= 3) Some(args(2)) else None
//
//  val config = ConfigFactory.parseString(s"""
//    akka.remote.artery.canonical.port = $port
//    ${seedNodes.map(sn => s"""akka.cluster.seed-nodes = ["$sn"]""").getOrElse("")}
//  """).withFallback(ConfigFactory.load())
//
//  val system = ActorSystem[ClusterProtocol](
//    ClusterManager(role, monitorActor, gossipActor),
//    "ClusterSystem",
//    config
//  )
//}
