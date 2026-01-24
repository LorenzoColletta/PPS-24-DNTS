package actors.model

import actors.monitor.MonitorActor.MonitorCommand
import akka.actor.typed.ActorRef
import domain.training.{NetworkGradient, Optimizer}
import domain.network.Model
import domain.data.Point2D

object ModelProtocol:

  sealed trait ModelCommand extends Serializable

  object ModelCommand:
    final case class  Initialize(model: Model, optimizer: Optimizer) extends ModelCommand
    final case class  ApplyGradients(grads: NetworkGradient)  extends ModelCommand
    final case class  GetModel(replyTo: ActorRef[Model])  extends ModelCommand
    final case class  SyncModel(remoteModel: Model)  extends ModelCommand
    final case class  TrainingCompleted(updatedModel: Model)  extends ModelCommand
    final case class  GetMetrics(replyTo: ActorRef[MonitorCommand.ViewUpdateResponse])  extends ModelCommand
    final case class  ExportToFile() extends ModelCommand
    final case class  GetPrediction(point: Point2D, replyTo: ActorRef[Double]) extends ModelCommand
