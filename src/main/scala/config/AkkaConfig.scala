package config

import actors.root.RootProtocol.NodeRole
import com.typesafe.config.ConfigFactory
import com.typesafe.config.Config

object AkkaConfig :

  def load(role: NodeRole, clusterIp: Option[String], clusterPort: Option[Int]): Config =
    ConfigFactory.parseString(
      s"""
        ${clusterPort.map(port => s"""akka.remote.artery.canonical.port = $port""").getOrElse("")}
        akka.cluster.roles = [${role.id}]
        ${clusterIp.map(ip => s"""akka.remote.artery.canonical.hostname = $ip""").getOrElse("")}
      """).withFallback(ConfigFactory.load())
