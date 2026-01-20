package actors.trainer

import domain.data.LabeledPoint2D
import domain.network.{Feature, HyperParams, Model}

/**
 * Defines the public API and Data Structures for the Trainer component.
 */
object TrainerProtocol:
  
  /**
   * Configuration parameters for a training session.
   *
   * @param dataset   The full list of labeled examples to train on.
   * @param features  The list of features to extract from the data points.
   * @param hp        Hyperparameters (learning rate, regularization).
   * @param epochs    Total number of passes through the dataset.
   * @param batchSize Number of examples to process per batch.
   * @param seed      Optional seed for deterministic shuffling.
   */
  case class TrainingConfig(
    dataset: List[LabeledPoint2D],
    features: List[Feature],
    hp: HyperParams,
    epochs: Int,
    batchSize: Int,
    seed: Option[Long] = None
  )

  
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

    /** Internal: Triggers processing of the next batch. */
    private[trainer] case NextBatch(epoch: Int, index: Int)
    