package actors.model

import actors.model.ModelActor.ModelCommand
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.Behavior

object ModelActor:

  export ModelProtocol.*

  def apply(): Behavior[ModelCommand] =
    Behaviors.setup: ctx =>
      ModelBehavior(ctx).idle()