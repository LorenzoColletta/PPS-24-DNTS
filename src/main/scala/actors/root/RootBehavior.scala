package actors.root

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}

import config.AppConfig
import actors.root.RootActor.*

/**
 * Encapsulates the behavior logic for the TrainerActor.
 *
 * @param context     The actor context providing access to the actor system.
 * @param role        The specific role of this node.
 * @param configPath  Optional file path to the configuration file used.
 * @param appConfig   Implicit global application configuration.
 */
class RootBehavior(
  context: ActorContext[RootCommand],
  role: NodeRole,
  configPath: Option[String]
)(using appConfig: AppConfig):

  def start(): Behavior[RootCommand] =
    Behaviors.empty
    