package actors.gossip.configuration

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import actors.discovery.DiscoveryProtocol.{DiscoveryCommand, NodesRefRequest}
import actors.gossip.GossipActor.{ControlCommand, GossipCommand}
import actors.trainer.TrainerProtocol.TrainingConfig
import config.AppConfig
import domain.network.Model

private[configuration] class ConfigurationBehavior(
  discoveryActor: ActorRef[DiscoveryCommand],
  timers: TimerScheduler[ConfigurationProtocol.ConfigurationCommand],
  config: AppConfig
):

  def active(
    cachedConfig: Option[(String, Model, TrainingConfig)] = None,
    gossip: Option[ActorRef[GossipCommand]] = None,
  ): Behavior[ConfigurationProtocol.ConfigurationCommand] =
    
    Behaviors.receive: (context, message) =>
      message match
        case ConfigurationProtocol.RegisterGossip(gossipActor) =>
          active(cachedConfig, Some(gossipActor))

        case ConfigurationProtocol.StartTickRequest =>
          context.log.info("Configuration: Starting initialization polling...")
          timers.startTimerWithFixedDelay(
            ConfigurationProtocol.TickRequest,
            ConfigurationProtocol.TickRequest,
            config.gossipInterval)
          Behaviors.same

        case ConfigurationProtocol.StopTickRequest =>
          context.log.info("Configuration Actor stopped.")
          timers.cancel(ConfigurationProtocol.TickRequest)
          Behaviors.same

        case ConfigurationProtocol.TickRequest =>
          if cachedConfig.isEmpty then
            context.log.debug("Gossip: Not initialized yet. Asking peers for Config...")
            discoveryActor ! NodesRefRequest(
              context.messageAdapter(peers => ConfigurationProtocol.WrappedRequestConfig(peers.toSet))
            )
          Behaviors.same

        case ConfigurationProtocol.WrappedRequestConfig(peers) =>
          gossip match
            case Some(gRef) =>
              peers.filter(_ != gRef).foreach { peer =>
                context.log.info(s"Configuration: Asking peer $peer for initial config")
                peer ! ConfigurationProtocol.RequestInitialConfig(context.self)
              }
            case None =>
              context.log.warn("Configuration: Cannot ask peers for config because Gossip actor is not registered yet.")
          Behaviors.same

        case ConfigurationProtocol.ShareConfig(seedID, model, trainConfig) =>
          context.log.info(s"Configuration: Saving config in local cache for Seed $seedID")

          gossip.foreach(_ ! GossipCommand.HandleControlCommand(
            ControlCommand.PrepareClient(seedID, model, trainConfig)
          ))
          active(cachedConfig = Some((seedID, model, trainConfig)), gossip = gossip)

        case ConfigurationProtocol.RequestInitialConfig(replyTo) =>
          cachedConfig match
            case Some((seedID, model, trainConfig)) =>
              context.log.info(s"Configuration: Sending ShareConfig to peer $replyTo")
              replyTo ! ConfigurationProtocol.ShareConfig(seedID, model, trainConfig)
            case None =>
              context.log.info(s"Config not found yet, cannot share.")
          Behaviors.same

        case ConfigurationProtocol.Stop =>
          context.log.info("Configuration: Stopping actor.")
          timers.cancelAll()
          Behaviors.stopped
