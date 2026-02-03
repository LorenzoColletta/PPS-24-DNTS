package actors.monitor

import domain.network.Model
import actors.trainer.TrainerActor.TrainingConfig
import domain.data.LabeledPoint2D

/**
 * Defines the public API for the Monitor component.
 */
object MonitorProtocol:

  /**
   * Root trait for all messages handled by the MonitorActor.
   */
  sealed trait MonitorMessage


  /**
   * Trait for all public commands handled by the MonitorActor.
   */
  sealed trait MonitorCommand extends MonitorMessage

  /** Protocol for the MonitorActor. */
  object MonitorCommand:
    
    /**
     * Starts the simulation setup (Master node only). Distributes data via Gossip.
     */
    case object StartSimulation extends MonitorCommand

    /**
     * Starts local training with a specific data slice, received by Master.
     *
     * @param trainSet  The local training set slice for this node.
     * @param testSet   The local test set slice for this node.
     */
    final case class StartWithData(
      trainSet: List[LabeledPoint2D], 
      testSet: List[LabeledPoint2D]
    ) extends MonitorCommand

    /** Initiates a global stop request from the user. */
    case object StopSimulation extends MonitorCommand

    /** Executes the actual stop logic. */
    case object InternalStop extends MonitorCommand

    /** Initiates a global pause request from the user. */
    case object PauseSimulation extends MonitorCommand

    /** Executes the actual pause logic. */
    case object InternalPause extends MonitorCommand

    /** Initiates a global resume request from the user. */
    case object ResumeSimulation extends MonitorCommand

    /** Executes the actual resume logic. */
    case object InternalResume extends MonitorCommand

    /**
     * Response payload containing the current snapshot of the training process.
     * Used to update the monitoring UI.
     *
     * @param model     The current state of the neural network.
     * @param trainLoss The loss value calculated on the current training batch.
     * @param testLoss  The loss value calculated on the test set.
     * @param consensus The consensus metric relative to the cluster.
     */
    final case class ViewUpdateResponse(
      epoch: Int,
      model: Model,
      trainLoss: Double,
      testLoss: Double,
      consensus: Double,
    ) extends MonitorCommand

    /** Simulates a node crash. */
    case object SimulateCrash extends MonitorCommand

    /**
     * Updates the count of active peers in the cluster.
     *
     * @param active The number of peers currently reachable.
     * @param total  The total number of peers discovered or expected.
     */
    final case class PeerCountChanged(
      active: Int, 
      total: Int
    ) extends MonitorCommand

    /**
     * Initializes the view state once the cluster is successfully created/connected.
     *
     * @param seed        The seed address of the cluster.
     * @param model       The simulation model structure.
     * @param config      The training configuration.
     */
    final case class Initialize(
      seed: String,
      model: Model,
      config: TrainingConfig,
    ) extends MonitorCommand

    /**
     * Handles a failure during the bootstrap phase.
     *
     * @param reason The error message describing why the bootstrap failed.
     */
    final case class ConnectionFailed(
      reason: String
    ) extends MonitorCommand

    /**
     * User request to export the current network state to a file.
     */
    case object RequestWeightsLog extends MonitorCommand

    /**
     * Signals that the training simulation has successfully completed all configured epochs.
     */
    case object SimulationFinished extends MonitorCommand


  /**
   * Trait for all private commands handled by the MonitorActor.
   */
  private[monitor] sealed trait PrivateMonitorCommand extends MonitorMessage

  /** Private protocol for the MonitorActor. */
  private[monitor] object PrivateMonitorCommand:

    /** Triggers local metric collection. */
    private[monitor] case object TickMetrics extends PrivateMonitorCommand
