package actors.trainer

import actors.ModelActor.ModelCommand
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.util.Timeout
import scala.concurrent.duration.*
import scala.util.{Failure, Random, Success}

import config.AppConfig
import domain.data.LabeledPoint2D
import domain.network.Model
import domain.training.{LossFunction, TrainingCore}


/**
 * Actor responsible for the actual training loop logic.
 * It manages the dataset iteration (epochs/batches), computes gradients using the
 * TrainingCore, and sends updates back to the ModelActor.
 */
object TrainerActor:

  export TrainerProtocol.*
  
  /**
   * Creates the TrainerActor behavior.
   *
   * @param modelActor    Reference to the ModelActor (holds the shared state).
   * @param lossFunction  Implicit loss function used for gradient calculation.
   * @param config        Implicit application configuration.
   */
  def apply(
    modelActor: ActorRef[ModelCommand],
  )(using lossFunction: LossFunction, config: AppConfig): Behavior[TrainerCommand] =
    idle(modelActor)

  
  private def idle(
    ma: ActorRef[ModelCommand]
  )(using LossFunction, AppConfig): Behavior[TrainerCommand] =

    Behaviors.receive: (ctx, msg) =>
      msg match
        case TrainerCommand.SetTrainConfig(trainConfig) =>
          ctx.log.info(s"Trainer: Setting Training Configuration")
          ready(ma, trainConfig)

        case TrainerCommand.CalculateMetrics(model, replyTo) =>
          replyTo ! MetricsCalculated(0.0, 0.0)
          Behaviors.same

        case TrainerCommand.Stop =>
          Behaviors.stopped

        case _ => Behaviors.unhandled

  private def ready(
    ma: ActorRef[ModelCommand],
    trainConfig: TrainingConfig,
  )(using LossFunction, AppConfig): Behavior[TrainerCommand] =
    
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

          ctx.self ! TrainerCommand.NextBatch(1, 0)
          training(ma, newTrainConfig, shuffledDataset, rand, 1, 0)

        case TrainerCommand.CalculateMetrics(model, replyTo) =>
          replyTo ! MetricsCalculated(0.0, 0.0)
          Behaviors.same

        case TrainerCommand.Stop =>
          Behaviors.stopped

        case _ => Behaviors.unhandled

  private def training(
    ma: ActorRef[ModelCommand],
    trainConfig: TrainingConfig,
    currentDataset: List[LabeledPoint2D],
    rand: Random,
    currentEpoch: Int,
    currentIdx: Int
  )(using lf: LossFunction, config: AppConfig): Behavior[TrainerCommand] =

    implicit val timeout: Timeout = 3.seconds

    Behaviors.withTimers: timers =>
      Behaviors.receive: (ctx, msg) =>
        msg match
          case TrainerCommand.NextBatch(epoch, idx) =>
            if epoch > trainConfig.epochs then
              ctx.log.info("Trainer: All epochs completed.")
              ready(ma, trainConfig)
            else
              val batch = currentDataset.slice(idx, idx + trainConfig.batchSize)

              if batch.isEmpty then
                val nextEpoch = epoch + 1
                val nextShuffled = rand.shuffle(trainConfig.trainSet)

                ctx.self ! TrainerCommand.NextBatch(nextEpoch, 0)
                training(ma, trainConfig, nextShuffled, rand, nextEpoch, 0)
              else
                ctx.ask[ModelCommand, Model](ma, ref => ModelCommand.GetModel(ref)) {
                  case Success(model) => TrainerCommand.ComputeGradients(model, batch, epoch, idx)
                  case Failure(_) => TrainerCommand.Stop
                }
                Behaviors.same

          case TrainerCommand.ComputeGradients(model, batch, epoch, idx) =>
            val (grads, _) = TrainingCore.computeBatchGradients(model, batch)
            ma ! ModelCommand.ApplyGradients(grads)

            val nextIdx = idx + trainConfig.batchSize
            timers.startSingleTimer(TrainerCommand.NextBatch(epoch, nextIdx), config.batchInterval)
            training(ma, trainConfig, currentDataset, rand, epoch, nextIdx)

          case TrainerCommand.CalculateMetrics(model, replyTo) =>
            val trainLoss = TrainingCore.computeDatasetLoss(model, trainConfig.trainSet)
            val testLoss = TrainingCore.computeDatasetLoss(model, trainConfig.testSet)

            replyTo ! MetricsCalculated(trainLoss, testLoss)
            Behaviors.same

          case TrainerCommand.Pause =>
            ctx.log.info(s"Trainer: Paused at Epoch $currentEpoch, Index $currentIdx")
            timers.cancelAll()
            paused(ma, trainConfig, currentDataset, rand, (currentEpoch, currentIdx))

          case TrainerCommand.Stop =>
            timers.cancelAll()
            Behaviors.stopped

          case _ => Behaviors.same

  private def paused(
    ma: ActorRef[ModelCommand],
    trainConfig: TrainingConfig,
    currentDataset: List[LabeledPoint2D],
    rand: Random,
    resumePos: (Int, Int)
  )(using LossFunction, AppConfig): Behavior[TrainerCommand] =

    Behaviors.receive: (ctx, msg) =>
      msg match
        case TrainerCommand.Resume =>
          ctx.log.info(s"Trainer: Resuming from Epoch ${resumePos._1}, Index ${resumePos._2}")
          ctx.self ! TrainerCommand.NextBatch(resumePos._1, resumePos._2)
          training(ma, trainConfig, currentDataset, rand, resumePos._1, resumePos._2)

        case TrainerCommand.CalculateMetrics(model, replyTo) =>
          val trainLoss = TrainingCore.computeDatasetLoss(model, trainConfig.trainSet)
          val testLoss = TrainingCore.computeDatasetLoss(model, trainConfig.testSet)

          replyTo ! MetricsCalculated(trainLoss, testLoss)
          Behaviors.same

        case TrainerCommand.Stop =>
          Behaviors.stopped

        case _ => Behaviors.same
