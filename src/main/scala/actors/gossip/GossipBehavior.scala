package actors.gossip

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import actors.gossip.GossipActor.{ControlCommand, GossipCommand}
import actors.model.ModelActor.ModelCommand
import actors.cluster.ClusterProtocol.{ClusterMemberCommand, StartSimulation, StopSimulation}
import actors.discovery.DiscoveryProtocol.{DiscoveryCommand, NodesRefRequest}
import actors.trainer.TrainerActor.TrainerCommand
import actors.trainer.TrainerActor.TrainingConfig
import actors.monitor.MonitorActor.MonitorCommand
import akka.actor.typed.scaladsl.TimerScheduler
import config.AppConfig
import domain.network.Model
import domain.training.Strategies.{Optimizers, Regularizers}



import scala.util.Random

/**
 * Encapsulates the behavior logic for the GossipActor.
 *
 * @param modelActor     Reference to the local ModelActor.
 * @param trainerActor   Reference to the local TrainerActor.
 * @param clusterManager Reference to the Cluster Manager.
 * @param timers         The scheduler for managing periodic gossip ticks.
 * @param config         Global application configuration.
 */
private[gossip] class GossipBehavior(
  modelActor: ActorRef[ModelCommand],
  trainerActor: ActorRef[TrainerCommand],
  clusterManager: ActorRef[ClusterMemberCommand],
  discoveryActor: ActorRef[DiscoveryCommand],
  timers: TimerScheduler[GossipCommand],
  config: AppConfig
):

  /**
   * Main operational state of the GossipActor.
   */
  def active(
              monitorInitialized: Boolean,
              monitorOptionActor: Option[ActorRef[MonitorCommand]],
              cachedConfig: Option[(String, Model, TrainingConfig)] = None
            ): Behavior[GossipCommand] =
    Behaviors.receive: (context, message) =>
      message match

        case GossipCommand.RegisterMonitor(monitorActor) =>
          active(monitorInitialized, Some(monitorActor), cachedConfig)

        case GossipCommand.ShareConfig(seedID, model, trainConfig) =>
          context.log.info("Gossip: Received Config from Root. Broadcasting to Cluster...")

          val cmd = ControlCommand.PrepareClient(seedID, model, trainConfig)

          discoveryActor ! NodesRefRequest(replyTo =
            context.messageAdapter(peers => GossipCommand.WrappedSpreadCommand(peers, cmd))
          )

          active(monitorInitialized = true, monitorOptionActor, Some((seedID, model, trainConfig)))

        case GossipCommand.StartGossipTick =>
          context.log.info("Gossip: Received Start signal. Starting gossip polling.")
          timers.startTimerWithFixedDelay(
            GossipCommand.TickGossip,
            GossipCommand.TickGossip,
            config.gossipInterval
          )
          Behaviors.same

        case GossipCommand.StopGossipTick =>
          context.log.info("Gossip: Stopping gossip polling.")
          timers.cancel(GossipCommand.TickGossip)
          Behaviors.same

        case GossipCommand.TickGossip =>
          discoveryActor ! NodesRefRequest(
            replyTo = context.messageAdapter(peers => GossipCommand.WrappedPeers(peers))
          )
          if !monitorInitialized then
            context.log.debug("Gossip: Not initialized yet. Asking peers for Config...")
            discoveryActor ! NodesRefRequest(
              context.messageAdapter(peers => GossipCommand.WrappedRequestConfig(peers.toSet))
            )
          Behaviors.same

        case GossipCommand.WrappedRequestConfig(peers) =>
          peers.filter(_ != context.self).foreach { peer =>
            peer ! GossipCommand.RequestInitialConfig(context.self)
          }
          Behaviors.same

        case GossipCommand.RequestInitialConfig(replyTo) =>
          cachedConfig match
            case Some((seedID, model, trainConfig)) =>
              context.log.info(s"Gossip: Peer $replyTo asked for config. Sending it.")
              replyTo ! GossipCommand.HandleControlCommand(
                ControlCommand.PrepareClient(seedID, model, trainConfig)
              )
            case None =>
              context.log.info(s"Config not found.")
          Behaviors.same

        case GossipCommand.DistributeDataset(trainSet , testSet) =>
          discoveryActor ! NodesRefRequest(
            replyTo = context.messageAdapter(peers =>
              GossipCommand.WrappedDistributeDataset(peers, trainSet, testSet)
            )
          )
          Behaviors.same

        case GossipCommand.WrappedDistributeDataset(peers, trainSet, testSet)  =>
          val totalNodes = peers.size
          if totalNodes > 0 then
            val chunkSize = trainSet.size / totalNodes

            peers.zipWithIndex.foreach: (peer, index) =>
              val from = index * chunkSize
              val until = if (index == totalNodes - 1) trainSet.size else (index + 1) * chunkSize

              val trainShard = trainSet.slice(from, until)

              peer ! GossipCommand.HandleDistributeDataset(trainShard, testSet)
          Behaviors.same

        case GossipCommand.HandleDistributeDataset(trainShard, testSet) =>
          trainerActor ! TrainerCommand.Start(trainShard, testSet)
          clusterManager ! StartSimulation
          Behaviors.same

        case GossipCommand.WrappedPeers(peers) =>
          val potentialPeers = peers.filter(_ != context.self)
          if monitorInitialized && potentialPeers.nonEmpty then
            val target = potentialPeers(Random.nextInt(potentialPeers.size))
            modelActor ! ModelCommand.GetModel(
              replyTo = context.messageAdapter(model => GossipCommand.SendModelToPeer(model, target))
            )
          Behaviors.same

        case GossipCommand.SendModelToPeer(model, target) =>
          context.log.info(s"Gossip: Sending Model  to peer $target")
          target ! GossipCommand.HandleRemoteModel(model)
          Behaviors.same

        case GossipCommand.HandleRemoteModel(remoteModel) =>
          if monitorInitialized then modelActor ! ModelCommand.SyncModel(remoteModel)
          Behaviors.same

        case GossipCommand.SpreadCommand(cmd) =>
          discoveryActor ! NodesRefRequest(
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
          context.log.info(s"Gossip: Executing remote control command: $cmd")

          cmd match
            case ControlCommand.PrepareClient(seedID, model, trainConfig) =>
              if !monitorInitialized then
                context.log.info(s"Gossip (CLIENT): Received Init Config from $seedID")

                monitorOptionActor.foreach(_ ! MonitorCommand.Initialize(seedID, model, trainConfig))

                val regularizationStrategy = Regularizers.fromConfig(trainConfig.hp.regularization)
                val optimizer = Optimizers.SGD(trainConfig.hp.learningRate, regularizationStrategy)
                modelActor ! ModelCommand.Initialize(model, optimizer, trainerActor)

                active(true, monitorOptionActor, Some((seedID, model, trainConfig)))
              else
                Behaviors.same
            case ControlCommand.GlobalPause =>
              trainerActor ! TrainerCommand.Pause
            case ControlCommand.GlobalResume =>
              trainerActor ! TrainerCommand.Resume
            case ControlCommand.GlobalStop =>
              clusterManager ! StopSimulation
              trainerActor ! TrainerCommand.Stop
            case _ =>
              context.log.info(s"Gossip: Not found remote control command: $cmd")

          Behaviors.same

        case _ =>
          context.log.warn("Gossip: Received unhandled gossip message.")
          Behaviors.unhandled
          