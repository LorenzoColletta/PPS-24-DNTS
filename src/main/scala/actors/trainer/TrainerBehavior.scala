package actors.trainer

import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import akka.util.Timeout
import scala.concurrent.duration.*
import scala.util.{Failure, Random, Success}

import config.AppConfig
import domain.data.LabeledPoint2D
import domain.network.Model
import domain.training.{LossFunction, TrainingCore}
import actors.trainer.TrainerActor.*
import actors.model.ModelActor.ModelCommand

/**
 * Encapsulates the behavior logic for the TrainerActor.
 *
 * @param timers        The scheduler for managing timed messages.
 * @param modelActor    A reference to the ModelActor.
 * @param lossFunction  Implicit loss function used during training process.
 * @param config        Implicit global application configuration.
 */
private[trainer] class TrainerBehavior(
  timers: TimerScheduler[TrainerMessage],
  modelActor: ActorRef[ModelCommand]
)(using lossFunction: LossFunction, config: AppConfig):

  implicit val timeout: Timeout = 3.seconds

  /**
   * Initial state: Waiting for configuration.
   */
  def idle(): Behavior[TrainerMessage] =
    Behaviors.receive: (ctx, msg) =>
      msg match
        case TrainerCommand.SetTrainConfig(trainConfig) =>
          ctx.log.info(s"Trainer: Setting Training Configuration")
          ready(trainConfig)

        case TrainerCommand.CalculateMetrics(_, replyTo) =>
          replyTo ! MetricsCalculated(0.0, 0.0, 0)
          Behaviors.same

        case TrainerCommand.Stop =>
          Behaviors.stopped

        case _ => Behaviors.unhandled

  /**
   * Configured state: Ready to start training.
   */
  private def ready(trainConfig: TrainingConfig): Behavior[TrainerMessage] =
    Behaviors.receive: (ctx, msg) =>
      msg match
        case TrainerCommand.Start(trainSet, testSet) =>
          ctx.log.info(s"Trainer: Starting with seed ${trainConfig.seed}...")

          val newTrainConfig = trainConfig.copy(
            trainSet = trainSet,
            testSet = testSet,
          )
          val rand = newTrainConfig.seed.map(s => new Random(s)).getOrElse(new Random())
          val shuffledDataset = rand.shuffle(newTrainConfig.trainSet)

          ctx.self ! PrivateTrainerCommand.NextBatch(1, 0)
          training(newTrainConfig, shuffledDataset, rand, 1, 0)

        case TrainerCommand.CalculateMetrics(_, replyTo) =>
          replyTo ! MetricsCalculated(0.0, 0.0, 0)
          Behaviors.same

        case TrainerCommand.Stop =>
          Behaviors.stopped

        case _ => Behaviors.unhandled

  /**
   * Active state: The main training loop.
   */
  private def training(
    trainConfig: TrainingConfig,
    currentDataset: List[LabeledPoint2D],
    rand: Random,
    currentEpoch: Int,
    currentIdx: Int
  ): Behavior[TrainerMessage] =

    implicit val timeout: Timeout = 3.seconds

    Behaviors.receive: (ctx, msg) =>
      msg match
        case PrivateTrainerCommand.NextBatch(epoch, idx) =>
          if epoch > trainConfig.epochs then
            ctx.log.info("Trainer: All epochs completed.")
            ready(trainConfig)
          else
            val batch = currentDataset.slice(idx, idx + trainConfig.batchSize)

            if batch.isEmpty then
              val nextEpoch = epoch + 1
              val nextShuffled = rand.shuffle(trainConfig.trainSet)

              ctx.self ! PrivateTrainerCommand.NextBatch(nextEpoch, 0)
              training(trainConfig, nextShuffled, rand, nextEpoch, 0)
            else
              ctx.ask[ModelCommand, Model](modelActor, ref => ModelCommand.GetModel(ref)) {
                case Success(model) => TrainerCommand.ComputeGradients(model, batch, epoch, idx)
                case Failure(_) => TrainerCommand.Stop
              }
              Behaviors.same

        case TrainerCommand.ComputeGradients(model, batch, epoch, idx) =>
          val (grads, _) = TrainingCore.computeBatchGradients(model, batch)
          modelActor ! ModelCommand.ApplyGradients(grads)

          val nextIdx = idx + trainConfig.batchSize
          timers.startSingleTimer(PrivateTrainerCommand.NextBatch(epoch, nextIdx), config.batchInterval)
          Behaviors.same

        case TrainerCommand.CalculateMetrics(model, replyTo) =>
          val trainLoss = TrainingCore.computeDatasetLoss(model, trainConfig.trainSet)
          val testLoss = TrainingCore.computeDatasetLoss(model, trainConfig.testSet)

          replyTo ! MetricsCalculated(trainLoss, testLoss, currentEpoch)
          Behaviors.same

        case TrainerCommand.Pause =>
          ctx.log.info(s"Trainer: Paused at Epoch $currentEpoch, Index $currentIdx")
          timers.cancelAll()
          paused(trainConfig, currentDataset, rand, (currentEpoch, currentIdx))

        case TrainerCommand.Stop =>
          timers.cancelAll()
          Behaviors.stopped

        case _ => Behaviors.same

  /**
   * Paused state.
   */
  private def paused(
    trainConfig: TrainingConfig,
    currentDataset: List[LabeledPoint2D],
    rand: Random,
    resumePos: (Int, Int)
  ): Behavior[TrainerMessage] =

    Behaviors.receive: (ctx, msg) =>
      msg match
        case TrainerCommand.Resume =>
          ctx.log.info(s"Trainer: Resuming from Epoch ${resumePos._1}, Index ${resumePos._2}")
          ctx.self ! PrivateTrainerCommand.NextBatch(resumePos._1, resumePos._2)
          training(trainConfig, currentDataset, rand, resumePos._1, resumePos._2)

        case TrainerCommand.CalculateMetrics(model, replyTo) =>
          val trainLoss = TrainingCore.computeDatasetLoss(model, trainConfig.trainSet)
          val testLoss = TrainingCore.computeDatasetLoss(model, trainConfig.testSet)

          replyTo ! MetricsCalculated(trainLoss, testLoss, resumePos._1)
          Behaviors.same

        case TrainerCommand.Stop =>
          Behaviors.stopped

        case _ => Behaviors.same
