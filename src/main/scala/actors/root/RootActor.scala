package actors.root

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.typesafe.config.Config
import config.AppConfig


/**
 * Actor responsible for bootstrapping the entire application
 * hierarchy based on the provided [[NodeRole]].
 */
object RootActor:

  export RootProtocol.*

  /**
   * Creates the RootActor behavior.
   *
   * @param role       The role passed via CLI.
   * @param configPath Optional path to the configuration file.
   * @param appConfig  Implicit application configuration.
   */
  def apply(
    role: NodeRole,
    configPath: Option[String],
    akkaConfig: Config
  )(using appConfig: AppConfig): Behavior[RootCommand] =
    Behaviors.setup: context =>
      new RootBehavior(context, role, configPath, akkaConfig).start()
