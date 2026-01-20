package actors

import actors.ModelActor.ModelCommand
import actors.gossip.GossipActor
import actors.gossip.GossipProtocol.*
import actors.monitor.MonitorProtocol.MonitorCommand
import actors.trainer.TrainerActor.TrainerCommand
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import domain.network.{Model, Network}
import org.scalatest.wordspec.AnyWordSpecLike
import config.{AppConfig, ProductionConfig}

class GossipActorTest extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  //TODO
}
