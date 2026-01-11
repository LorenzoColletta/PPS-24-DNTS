package actors

import domain.network.{Model, Network, Feature, HyperParams}
import domain.data.LabeledPoint2D
import domain.training.TrainingService
import akka.actor.typed.{Behavior, ActorRef}
import akka.actor.typed.scaladsl.Behaviors


case class TrainingConfig(dataset: List[LabeledPoint2D],
                          features: List[Feature],
                          hp: HyperParams,
                          epochs: Int,
                          batchSize: Int,
                          seed: Option[Long] = None)

enum TrainerCommand:
  case StartTraining(initialNet: Network, config: TrainingConfig)

object TrainerActor:
  def apply(modelActor: ActorRef[ModelCommand]): Behavior[TrainerCommand] =
    Behaviors.receive: (context, message) =>
      message match
        case TrainerCommand.StartTraining(net, cfg) =>
          context.log.info("TrainerActor: Start Training")
          GossipActor
          val (updatedNet, _) = TrainingService.train(
            net, cfg.dataset, cfg.features, cfg.hp, cfg.epochs, cfg.batchSize, cfg.seed
          )
          val updatedModel = Model(updatedNet, cfg.features)
          modelActor ! ModelCommand.TrainingCompleted(updatedModel)
          Behaviors.same