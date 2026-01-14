package actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout
import scala.concurrent.duration.*
import scala.util.{Random, Success, Failure}

import domain.network.{Network, Feature, HyperParams}
import domain.data.LabeledPoint2D
import domain.training.TrainingCore
import domain.training.LossFunction
import actors.ModelActor.ModelCommand

/**
 * Actor responsible for the actual training loop logic.
 * It manages the dataset iteration (epochs/batches), computes gradients using the
 * TrainingCore, and sends updates back to the ModelActor.
 */
object TrainerActor:

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
    case ComputeGradients(net: Network, batch: List[LabeledPoint2D], epoch: Int, index: Int)

    /** Internal: Triggers processing of the next batch. */
    private[TrainerActor] case NextBatch(epoch: Int, index: Int)


  private final val BatchInterval = 10.millis

  /**
   * Creates the TrainerActor behavior.
   *
   * @param modelActor   Reference to the ModelActor (holds the shared state).
   * @param lossFunction Implicit loss function used for gradient calculation.
   */
  def apply(modelActor: ActorRef[ModelCommand])(using LossFunction): Behavior[TrainerCommand] =
    ready(modelActor)

  private def ready(ma: ActorRef[ModelCommand])(using LossFunction): Behavior[TrainerCommand] =
    Behaviors.receive: (ctx, msg) =>
      msg match
        case TrainerCommand.Start(cfg) =>
          ctx.log.info(s"Trainer: Starting with seed ${cfg.seed}...")

          val rand = cfg.seed.map(s => new Random(s)).getOrElse(new Random())
          val shuffledDataset = rand.shuffle(cfg.dataset)

          ctx.self ! TrainerCommand.NextBatch(1, 0)
          training(ma, cfg, shuffledDataset, rand, 1, 0)

        case TrainerCommand.Stop =>
          Behaviors.stopped

        case _ => Behaviors.unhandled

  private def training(
    ma: ActorRef[ModelCommand],
    cfg: TrainingConfig,
    currentDataset: List[LabeledPoint2D],
    rand: Random,
    currentEpoch: Int,
    currentIdx: Int
  )(using lf: LossFunction): Behavior[TrainerCommand] =

    implicit val timeout: Timeout = 3.seconds

    Behaviors.withTimers: timers =>
      Behaviors.receive: (ctx, msg) =>
        msg match
          case TrainerCommand.NextBatch(epoch, idx) =>
            if epoch > cfg.epochs then
              ctx.log.info("Trainer: All epochs completed.")
              ready(ma)
            else
              val batch = currentDataset.slice(idx, idx + cfg.batchSize)

              if batch.isEmpty then
                val nextEpoch = epoch + 1
                val nextShuffled = rand.shuffle(cfg.dataset)

                ctx.self ! TrainerCommand.NextBatch(nextEpoch, 0)
                training(ma, cfg, nextShuffled, rand, nextEpoch, 0)
              else
                ctx.ask[ModelCommand, Network](ma, ref => ModelCommand.GetModel(ref)) {
                  case Success(net) => TrainerCommand.ComputeGradients(net, batch, epoch, idx)
                  case Failure(_) => TrainerCommand.Stop
                }
                Behaviors.same

          case TrainerCommand.ComputeGradients(network, batch, epoch, idx) =>
            val (grads, loss) = TrainingCore.computeBatchGradients(network, batch, cfg.features)
            ma ! ModelCommand.ApplyGradients(grads)

            val nextIdx = idx + cfg.batchSize
            timers.startSingleTimer(TrainerCommand.NextBatch(epoch, nextIdx), BatchInterval)
            training(ma, cfg, currentDataset, rand, epoch, nextIdx)

          case TrainerCommand.Pause =>
            ctx.log.info(s"Trainer: Paused at Epoch $currentEpoch, Index $currentIdx")
            timers.cancelAll()
            paused(ma, cfg, currentDataset, rand, (currentEpoch, currentIdx))

          case TrainerCommand.Stop =>
            timers.cancelAll()
            Behaviors.stopped

          case _ => Behaviors.same

  private def paused(
    ma: ActorRef[ModelCommand],
    cfg: TrainingConfig,
    currentDataset: List[LabeledPoint2D],
    rand: Random,
    resumePos: (Int, Int)
  )(using LossFunction): Behavior[TrainerCommand] =

    Behaviors.receive: (ctx, msg) =>
      msg match
        case TrainerCommand.Resume =>
          ctx.log.info(s"Trainer: Resuming from Epoch ${resumePos._1}, Index ${resumePos._2}")
          ctx.self ! TrainerCommand.NextBatch(resumePos._1, resumePos._2)
          training(ma, cfg, currentDataset, rand, resumePos._1, resumePos._2)

        case TrainerCommand.Stop =>
          Behaviors.stopped

        case _ => Behaviors.same
