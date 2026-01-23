package actors.model

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import domain.model.ModelTasks
import domain.network.Model
import domain.training.{NetworkGradient, Optimizer}

object ModelActor:

  export ModelProtocol.*

  def apply(): Behavior[ModelCommand] =
    Behaviors.setup: ctx =>
      ModelBehavior(ctx).idle()