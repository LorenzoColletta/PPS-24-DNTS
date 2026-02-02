package actors.model

import actors.model.ModelActor.ModelCommand
import actors.monitor.MonitorActor.MonitorCommand
import actors.trainer.TrainerActor.TrainerCommand
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import config.{AppConfig, ProductionConfig}
import domain.model.ModelTasks
import domain.training.Optimizer
import domain.training.Consensus.*
import domain.training.consensus.ConsensusMetric.given
import domain.network.Model
import domain.serialization.Exporters.given
import domain.serialization.Exporters.Exporter
import ModelConstants.*

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

private object ModelConstants:
  val InitialEpoch: Int = 0
  val MaxConsensus: Double = 1.0
  val ConsensusSmoothing: Double = 1.0

private[model] class ModelBehavior(context: ActorContext[ModelCommand],config: AppConfig):


  def idle(): Behavior[ModelCommand] =
    Behaviors.receive: (ctx, msg) =>
      msg match
        case ModelCommand.Initialize(model, optimizer, trainerActor) =>
          ctx.log.info("Model: Model initialized. Switching to active state.")

          given Optimizer = optimizer

          active(
            currentModel = model,
            currentEpoch = InitialEpoch,
            currentConsensus = MaxConsensus,
            trainerActor
          )

        case _ => Behaviors.unhandled

  private def active(
                      currentModel: Model,
                      currentEpoch: Int,
                      currentConsensus: Double,
                      trainerActor: ActorRef[TrainerCommand]
                    )(using Optimizer): Behavior[ModelCommand] =
    Behaviors.receive: (_, message) =>
      message match
        case ModelCommand.ApplyGradients(grads) =>
          val (newModel, _) = ModelTasks.applyGradients(grads).run(currentModel)
          active(
            currentModel = newModel,
            currentEpoch = currentEpoch,
            currentConsensus = currentConsensus,
            trainerActor = trainerActor
          )

        case ModelCommand.SyncModel(remoteModel) =>
          val (newModel, _) = ModelTasks.mergeWith(remoteModel).run(currentModel)
          val divergence = currentModel.network.divergenceFrom(remoteModel.network)
          val newConsensus = ConsensusSmoothing / (ConsensusSmoothing + divergence)
          active(
            currentModel = newModel,
            currentEpoch = currentEpoch,
            currentConsensus = newConsensus,
            trainerActor = trainerActor
          )

        case ModelCommand.GetPrediction(point, replyTo) =>
          val prediction = currentModel.predict(point)
          replyTo ! prediction
          Behaviors.same

        case ModelCommand.GetModel(replyTo) =>
          replyTo ! currentModel
          Behaviors.same

        case ModelCommand.GetMetrics(replyTo) =>
          trainerActor ! TrainerCommand.CalculateMetrics(
            currentModel,
            replyTo = context.messageAdapter(m => ModelCommand.InternalMetricsResult(m, replyTo))
          )
          Behaviors.same

        case ModelCommand.InternalMetricsResult(metrics, monitorReplyTo) =>
          monitorReplyTo ! MonitorCommand.ViewUpdateResponse(
            epoch = metrics.epoch,
            model = currentModel,
            trainLoss = metrics.trainLoss,
            testLoss = metrics.testLoss,
            consensus = currentConsensus
          )
          Behaviors.same

        case ModelCommand.ExportToFile() =>
          val jsonModel = summon[Exporter[Model]].jsonExport(currentModel)
          val fileName = config.netLogFileName
          val path = Paths.get(fileName)
          try {
            Files.write(path, jsonModel.getBytes(StandardCharsets.UTF_8))
            context.log.info(s"Model exported in: $fileName")
          } catch {
            case e: Exception =>
              context.log.error(s"Error in exportation of $fileName: ${e.getMessage}")
          }
          Behaviors.same
        case _ => Behaviors.unhandled