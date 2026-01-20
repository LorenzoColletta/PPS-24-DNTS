package actors.trainer

import akka.actor.typed.ActorRef

import domain.data.LabeledPoint2D
import domain.network.{Feature, HyperParams, Model}

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
   */
  case class MetricsCalculated(trainLoss: Double, testLoss: Double)


  /** Protocol for the TrainerActor. */
  enum TrainerCommand:
    /** Starts the training process with the provided configuration. */
    case Start(config: TrainingConfig)

    /** Pauses the training loop. Keeps the current state (epoch/index). */
    case Pause

    /** Resumes training from the paused state. */
    case Resume

    /** Stops the training and terminates the actor. */
    case Stop

    /** Internal: Carries the current network state to compute gradients against. */
    case ComputeGradients(model: Model, batch: List[LabeledPoint2D], epoch: Int, index: Int)

    /**
     * Request to compute losses on train and test datasets.
     *
     * @param model   The model snapshot to evaluate.
     * @param replyTo The actor waiting for the results.
     */
    case CalculateMetrics(model: Model, replyTo: ActorRef[MetricsCalculated])

    /** Internal: Triggers processing of the next batch. */
    private[trainer] case NextBatch(epoch: Int, index: Int)
    