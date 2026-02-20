package actors.trainer

import akka.actor.typed.ActorRef
import domain.data.LabeledPoint2D
import domain.network.{Feature, HyperParams, Model}
import actors.gossip.GossipActor.GossipCommand
import actors.gossip.configuration.ConfigurationProtocol.ConfigurationCommand
import actors.gossip.consensus.ConsensusProtocol.ConsensusCommand
import actors.monitor.MonitorActor.MonitorCommand

/**
 * Defines the public API and Data Structures for the Trainer component.
 */
object TrainerProtocol:

  /**
   * Configuration parameters for a training session.
   *
   * @param trainSet  The list of labeled examples to train on.
   * @param testSet   The list of labeled examples to test on the network.
   * @param features  The list of features to extract from the data points.
   * @param hp        Hyperparameters (learning rate, regularization).
   * @param epochs    Total number of passes through the dataset.
   * @param batchSize Number of examples to process per batch.
   * @param seed      Optional seed for deterministic shuffling.
   */
  case class TrainingConfig(
    trainSet: List[LabeledPoint2D],
    testSet: List[LabeledPoint2D],
    features: List[Feature],
    hp: HyperParams,
    epochs: Int,
    batchSize: Int,
    seed: Option[Long] = None
  )

  /** 
   * Response carrying the calculated full-dataset metrics.
   *
   * @param trainLoss The loss value computed on the training set.
   * @param testLoss  The loss value computed on the test set.
   * @param epoch     The actual epoch
   */
  case class MetricsCalculated(
    trainLoss: Double,
    testLoss: Double,
    epoch: Int
  )


  /**
   * Root trait for all messages handled by the TrainerActor.
   */
  sealed trait TrainerMessage

  
  /**
   * Trait for all public commands handled by the TrainerActor.
   */
  sealed trait TrainerCommand extends TrainerMessage

  /** Protocol for the TrainerActor. */
  object TrainerCommand:

    /**
     * Registers the references to auxiliary services (Monitor and Gossip).
     *
     * @param monitor The reference to the local [[MonitorActor]].
     * @param gossip  The reference to the local [[GossipActor]].
     */
    final case class RegisterServices(
                                       monitor: ActorRef[MonitorCommand],
                                       gossip: ActorRef[GossipCommand],
                                       configuration: ActorRef[ConfigurationCommand],
                                       consensus: ActorRef[ConsensusCommand]
    ) extends TrainerCommand

    /** 
     * Starts the training process with the provided datasets. 
     *
     * @param trainSet  The local training set slice for this node.
     * @param testSet   The local test set slice for this node.
     */
    final case class Start(
      trainSet: List[LabeledPoint2D], 
      testSet: List[LabeledPoint2D]
    ) extends TrainerCommand

    /** Pauses the training loop. Keeps the current state (epoch/index). */
    case object Pause extends TrainerCommand

    /** Resumes training from the paused state. */
    case object Resume extends TrainerCommand

    /** Stops the training and terminates the actor. */
    case object Stop extends TrainerCommand

    /** 
     * Carries the current network state to compute gradients against. 
     * 
     * @param model The current snapshot of the Neural Network weights.
     * @param batch The subset of data points to calculate gradients on.
     * @param epoch The current epoch number.
     * @param index The batch index within the epoch.
     */
    final case class ComputeGradients(
      model: Model, 
      batch: List[LabeledPoint2D], 
      epoch: Int, 
      index: Int
    ) extends TrainerCommand

    /**
     * Request to compute losses on train and test datasets.
     *
     * @param model   The model snapshot to evaluate.
     * @param replyTo The actor waiting for the results.
     */
    final case class CalculateMetrics(
      model: Model, 
      replyTo: ActorRef[MetricsCalculated]
    ) extends TrainerCommand

    /**
     * Set the configuration for the training phase.
     *
     * @param trainConfig The configuration.
     */
    final case class SetTrainConfig(
      trainConfig: TrainingConfig
    ) extends TrainerCommand

  /**
   * Trait for all private commands handled by the TrainerActor.
   */
  private[trainer] sealed trait PrivateTrainerCommand extends TrainerMessage

  /** Private protocol for the TrainerActor. */
  private[trainer] object PrivateTrainerCommand:

    /** 
     * Triggers processing of the next batch.
     * 
     * @param epoch The epoch that is about to be processed.
     * @param index The index of the next batch to fetch from the dataset.
     */
    private[trainer] final case class NextBatch(
       epoch: Int, 
       index: Int
    ) extends PrivateTrainerCommand
    