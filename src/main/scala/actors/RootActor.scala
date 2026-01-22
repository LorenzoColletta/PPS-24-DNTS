package actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors


object RootActor:

  sealed trait RootCommand

  object RootCommand:
    case object SeedStartSimulation extends RootCommand


  def apply(role: String, configPath: Option[String]): Behavior[RootCommand] =
    Behaviors.setup { context =>


      Behaviors.same
    }
