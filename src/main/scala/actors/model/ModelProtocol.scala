package actors.model

import actors.monitor.MonitorActor.MonitorCommand
import actors.trainer.TrainerActor.TrainerCommand
import actors.trainer.TrainerProtocol.MetricsCalculated
import akka.actor.typed.ActorRef
import domain.training.{NetworkGradient, Optimizer}
import domain.network.Model
import domain.data.Point2D

/**
 * Defines the public API for the Model component.
 */
object ModelProtocol:

  /**
   * Root trait for all messages handled by the ModelActor.
   */
  sealed trait ModelCommand extends Serializable

  /** Protocol for the ModelActor. */
  object ModelCommand:

    /**
     * Initializes the ModelActor with the initial neural network structure and training components.
     *
     * @param model        The initial neural network model.
     * @param optimizer    The optimization strategy to be used for updates.
     * @param trainerActor The reference to the associated TrainerActor.
     */
    final case class  Initialize(
                                  model: Model,
                                  optimizer: Optimizer,
                                  trainerActor: ActorRef[TrainerCommand]
                                ) extends ModelCommand

    /**
     * Updates the local model parameters using the provided gradients.
     *
     * @param grads The gradients calculated during the training step.
     */
    final case class  ApplyGradients(grads: NetworkGradient) extends ModelCommand

    /**
     * Requests the current Model.
     *
     * @param replyTo The actor reference that will receive the current Model.
     */
    final case class  GetModel(replyTo: ActorRef[Model])  extends ModelCommand

    /**
     * Synchronizes the local model with a model received from a remote peer.
     *
     * @param remoteModel The model received via gossip from another node.
     */
    final case class  SyncModel(remoteModel: Model)  extends ModelCommand

    /**
     * Triggers the calculation of metrics.
     *
     * @param replyTo The monitor reference that will receive the formatted ViewUpdateResponse.
     */
    final case class  GetMetrics(replyTo: ActorRef[MonitorCommand.ViewUpdateResponse])  extends ModelCommand

    /**
     * Internal message used to wrap the results of metric calculations.
     *
     * @param metrics The raw metrics calculated by the Trainer.
     * @param replyTo The original monitor reference to respond to.
     */
    final case class  InternalMetricsResult(
                                             metrics: MetricsCalculated,
                                             replyTo: ActorRef[MonitorCommand.ViewUpdateResponse]
                                           ) extends ModelCommand

    /**
     * Request to write the current model to the file
     */
    final case class  ExportToFile() extends ModelCommand

    /**
     * Performs a forward pass on the current network to predict the output for a given point.
     *
     * @param point   The input features (Point2D).
     * @param replyTo The actor reference that will receive the predicted Double value.
     */
    final case class  GetPrediction(
                                     point: Point2D,
                                     replyTo: ActorRef[Double]
                                   ) extends ModelCommand
