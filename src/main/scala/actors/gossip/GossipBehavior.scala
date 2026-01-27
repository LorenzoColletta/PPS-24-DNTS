package actors.gossip

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import actors.gossip.GossipActor.{ControlCommand, GossipCommand}
import actors.model.ModelActor.ModelCommand
import actors.cluster.{ClusterCommand, NodesRefRequest, StartSimulation}
import actors.trainer.TrainerActor.TrainerCommand
import actors.monitor.MonitorActor.MonitorCommand
import config.{AppConfig, ProductionConfig}
import domain.dataset.{DataModelFactory, DatasetGenerator}
import domain.network.Model

import scala.util.Random

private[gossip] class GossipBehavior(
                                      context: ActorContext[GossipCommand],
                                      modelActor: ActorRef[ModelCommand],
                                      monitorActor: ActorRef[MonitorCommand],
                                      trainerActor: ActorRef[TrainerCommand],
                                      clusterManager: ActorRef[ClusterCommand],
                                      config: AppConfig
                                    ):

  def active(): Behavior[GossipCommand] =
    Behaviors.receive: (context, message) =>
      message match

        case GossipCommand.TickGossip =>
          clusterManager ! NodesRefRequest(
            replyTo = context.messageAdapter(peers => GossipCommand.WrappedPeers(peers))
          )

          Behaviors.same

        case GossipCommand.WrappedPeers(peers) =>
          val potentialPeers = peers.filter(_ != context.self)
          if potentialPeers.nonEmpty then
            val target = potentialPeers(Random.nextInt(potentialPeers.size))
            modelActor ! ModelCommand.GetModel(
              replyTo = context.messageAdapter(model => GossipCommand.SendModelToPeer(model, target))
            )
          Behaviors.same

        case GossipCommand.SendModelToPeer(model, target) =>
          context.log.info(s"Gossip: Sending Model  to peer ${target}")
          target ! GossipCommand.HandleRemoteModel(model)
          Behaviors.same
        case GossipCommand.InitLocalDataset(size, strategy) =>

          val localSeed = Some(context.self.path.address.port.getOrElse(0).toLong)
          val datasetModel = DataModelFactory.create(strategy, localSeed)(using ProductionConfig.space)
          val localPoints  = DatasetGenerator.generate(size, datasetModel)

          trainerActor ! TrainerCommand.UpdateDataset(localPoints)

          context.log.info(s"Dataset initialized with $size points using seed $localSeed")
          Behaviors.same
        case GossipCommand.HandleRemoteModel(remoteModel) =>
          modelActor ! ModelCommand.SyncModel(remoteModel)
          Behaviors.same

        case GossipCommand.SpreadCommand(cmd) =>
          clusterManager ! NodesRefRequest(
            replyTo = context.messageAdapter(peers =>
              GossipCommand.WrappedSpreadCommand(peers, cmd)
            )
          )
          Behaviors.same

        case GossipCommand.WrappedSpreadCommand(peers, cmd) =>
          val otherPeers = peers.filter(_ != context.self)
          otherPeers.foreach ( peer =>
            peer ! GossipCommand.HandleControlCommand(cmd)
          )
          Behaviors.same

        case GossipCommand.HandleControlCommand(cmd) =>

          context.log.info(s"Executing remote control command: $cmd")

          cmd match
            case ControlCommand.GlobalStart =>
              context.self ! GossipCommand.InitLocalDataset(config.datasetSize, config.datasetStrategy)
              clusterManager ! StartSimulation
              monitorActor ! MonitorCommand.StartSimulation
            case ControlCommand.GlobalPause =>
              monitorActor ! MonitorCommand.InternalPause
              trainerActor ! TrainerCommand.Pause
            case ControlCommand.GlobalResume =>
              monitorActor ! MonitorCommand.InternalResume
              trainerActor ! TrainerCommand.Resume
            case ControlCommand.GlobalStop =>
              monitorActor ! MonitorCommand.InternalStop
              trainerActor ! TrainerCommand.Stop

          Behaviors.same

        case _ =>
          context.log.warn("Received unhandled gossip message.")
          Behaviors.unhandled