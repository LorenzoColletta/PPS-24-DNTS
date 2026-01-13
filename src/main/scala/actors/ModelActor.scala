package actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import domain.network.{Network, Model}
import domain.training.{NetworkGradient, Optimizer}

// Definizione del protocollo tramite Enum (Algebraic Data Type)
enum ModelCommand:
  case ApplyGradients(grads: NetworkGradient)
  case GetModel(replyTo: ActorRef[Network])
  case SyncModel(remoteModel: Network)
  case TrainingCompleted(updatedModel: Model)

