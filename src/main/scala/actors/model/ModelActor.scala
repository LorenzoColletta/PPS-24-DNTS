package actors.model

import actors.model.ModelActor.ModelCommand
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.Behavior
import config.{AppConfig, ProductionConfig}

object ModelActor:

  export ModelProtocol.*

  def apply(): Behavior[ModelCommand] =
    val config : AppConfig = ProductionConfig
    Behaviors.setup: ctx =>
      ModelBehavior(ctx, config).idle()