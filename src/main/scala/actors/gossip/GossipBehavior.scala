package actors.gossip

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}

import actors.gossip.GossipActor.{ControlCommand, GossipCommand}
import actors.model.ModelActor.ModelCommand
import actors.cluster.{ClusterCommand, NodesRefRequest, StartSimulation}
import actors.trainer.TrainerActor.TrainerCommand
import actors.monitor.MonitorActor.MonitorCommand
import domain.network.Model
import domain.serialization.ModelSerializers.given
import domain.serialization.NetworkSerializers.given
import domain.serialization.LinearAlgebraSerializers.given
import domain.network.Activations.given
import domain.serialization.ControlCommandSerializers.given
import domain.serialization.Serializer
import scala.util.{Success, Failure}

import scala.util.Random

private[gossip] class GossipBehavior(
                                      context: ActorContext[GossipCommand],
                                      modelActor: ActorRef[ModelCommand],
                                      monitorActor: ActorRef[MonitorCommand],
                                      trainerActor: ActorRef[TrainerCommand],
                                      clusterManager: ActorRef[ClusterCommand]
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
          val serializer = summon[Serializer[Model]]
          val serializedBytes = serializer.serialize(model)
          context.log.info(s"Gossip: Sending serialized Model (${serializedBytes.length} bytes) to peer")
          target ! GossipCommand.HandleRemoteModel(serializedBytes)
          Behaviors.same

        case GossipCommand.HandleRemoteModel(bytes) =>
          val serializer = summon[Serializer[Model]]
          serializer.deserialize(bytes) match
            case Success(remoteModel) =>
              context.log.info("Remote Model received and deserialized successfully.")
              modelActor ! ModelCommand.SyncModel(remoteModel)
            case Failure(exception) =>
              context.log.error(s"Failed to deserialize remote Model: ${exception.getMessage}")
          Behaviors.same

        case GossipCommand.SpreadCommand(cmd) =>
          clusterManager ! NodesRefRequest(
            replyTo = context.messageAdapter(peers =>
              GossipCommand.InternalExecuteSpread(cmd, peers)
            )
          )
          Behaviors.same

        case GossipCommand.InternalExecuteSpread(cmd, peers) =>
          val serializer = summon[Serializer[ControlCommand]]
          val serializedCmd = serializer.serialize(cmd)

          val otherPeers = peers.filter(_ != context.self)
          context.log.info(s"Control command $cmd sent in broadcast to ${otherPeers.size} peer.")

          otherPeers.foreach { peer =>
            peer ! GossipCommand.HandleControlCommand(serializedCmd)
          }

          Behaviors.same

        case GossipCommand.HandleControlCommand(bytes) =>

          val serializer = summon[Serializer[ControlCommand]]

          serializer.deserialize(bytes) match
            case Success(cmd) =>
              context.log.info(s"Executing remote control command: $cmd")
              cmd match
                case ControlCommand.GlobalStart =>
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
            case Failure(ex) =>
              context.log.error(s"Error deserialize: ${ex.getMessage}")

          Behaviors.same

        case _ =>
          context.log.warn("Received unhandled gossip message.")
          Behaviors.unhandled