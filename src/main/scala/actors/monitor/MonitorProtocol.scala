package actors.monitor

import domain.network.Model
import actors.trainer.TrainerActor.TrainingConfig

/**
 * Defines the public API for the Monitor component.
 */
object MonitorProtocol:

  /** Commands handled by the MonitorActor. */
  enum MonitorCommand:
    /**
     * Starts the simulation setup (Master node only). Distributes data via Gossip.
     *
     * @param config The global training configuration to be sliced and distributed.
     */
    case StartSimulation(config: TrainingConfig)

    /**
     * Starts local training with a specific data slice, received by Master.
     *
     * @param config The configuration containing the local dataset slice for this node.
     */
    case StartWithData(config: TrainingConfig)

    /** Initiates a global stop request from the user. */
    case StopSimulation

    /** Executes the actual stop logic. */
    case InternalStop

    /** Initiates a global pause request from the user. */
    case PauseSimulation

    /** Executes the actual pause logic. */
    case InternalPause

    /** Initiates a global resume request from the user. */
    case ResumeSimulation

    /** Executes the actual resume logic. */
    case InternalResume

    /** Triggers local metric collection. */
    case TickMetrics

    /**
     * Response payload containing the current snapshot of the training process.
     * Used to update the monitoring UI.
     *
     * @param model     The current state of the neural network.
     * @param trainLoss The loss value calculated on the current training batch.
     * @param testLoss  The loss value calculated on the test set.
     * @param consensus The consensus metric relative to the cluster.
     */
    case ViewUpdateResponse(
      epoch: Int,
      model: Model,
      trainLoss: Double,
      testLoss: Double,
      consensus: Double,
    )

    /** Simulates a node crash. */
    case SimulateCrash

    /**
     * Updates the count of active peers in the cluster.
     *
     * @param active The number of peers currently reachable.
     * @param total  The total number of peers discovered or expected.
     */
    case PeerCountChanged(active: Int, total: Int)

    /**
     * Initializes the view state once the cluster is successfully created/connected.
     *
     * @param seed        The seed address of the cluster.
     * @param model       The simulation model structure.
     * @param config      The training configuration.
     */
    case Initialize(
      seed: String,
      model: Model,
      config: TrainingConfig,
    )

    /**
     * Handles a failure during the bootstrap phase.
     *
     * @param reason The error message describing why the bootstrap failed.
     */
    case ConnectionFailed(reason: String)

    /**
     * User request to export the current network state to a file.
     */
    case RequestWeightsLog
