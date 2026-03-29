package config

import actors.root.RootProtocol.NodeRole
import com.typesafe.config.ConfigFactory
import com.typesafe.config.Config
import java.net.NetworkInterface
import scala.jdk.CollectionConverters._

object AkkaConfig :

  private def getLocalIp: String =
    NetworkInterface.getNetworkInterfaces.asScala
      .flatMap(_.getInetAddresses.asScala)
      .collect {
        case addr if !addr.isLoopbackAddress && addr.isInstanceOf[java.net.Inet4Address] =>
          addr.getHostAddress
      }
      .toList
      .lastOption
      .getOrElse("127.0.0.1")

  def load(role: NodeRole, clusterIp: Option[String], clusterPort: Option[Int]): Config =

    val port = clusterPort.getOrElse("5082")
    val seed =
      clusterIp match
        case Some(ip) =>
          s""""akka://ClusterSystem@$ip""""
        case None =>
          s""""akka://ClusterSystem@$getLocalIp:$port""""

    ConfigFactory.parseString(
      s"""
        akka.remote.artery.canonical.hostname = $getLocalIp
        akka.remote.artery.canonical.port = $port
        akka.cluster.roles = [${role.id}]
        akka.cluster.seed-nodes = [$seed]
      """).withFallback(ConfigFactory.load())