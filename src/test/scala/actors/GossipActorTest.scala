package actors

import actors.ModelActor.ModelCommand
import actors.gossip.GossipActor
import actors.gossip.GossipProtocol.*
import actors.monitor.MonitorProtocol.MonitorCommand
import actors.trainer.TrainerActor.TrainerCommand
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import domain.network.{Model, Network}
import org.scalatest.wordspec.AnyWordSpecLike
import config.{AppConfig, ProductionConfig}

class GossipActorTest extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  given config: AppConfig = ProductionConfig

  "A GossipActor" should {

    "send its model to a peer when TickGossip occurs" in {
      val modelProbe = createTestProbe[ModelCommand]()
      val monitorProbe = createTestProbe[MonitorCommand]()
      val trainerProbe = createTestProbe[TrainerCommand]()
      val peerProbe = createTestProbe[GossipCommand]()

      val gossipActor = spawn(GossipActor(modelProbe.ref, monitorProbe.ref, trainerProbe.ref))

      // Forniamo un peer al GossipActor
      gossipActor ! GossipCommand.UpdatePeers(Set(peerProbe.ref))

      // Simuliamo il tick del timer
      gossipActor ! GossipCommand.TickGossip

      // 1. Il GossipActor deve chiedere il modello al ModelActor
      val getModelMsg = modelProbe.expectMessageType[ModelCommand.GetModel]

      // 2. Rispondiamo alla richiesta del ModelActor
      val dummyModel = Model(Network(List.empty), List.empty)
      getModelMsg.replyTo ! dummyModel

      // 3. Il peer deve ricevere il modello tramite HandleRemoteModel
      peerProbe.expectMessage(GossipCommand.HandleRemoteModel(dummyModel))
    }

    "sync the local model when receiving HandleRemoteModel" in {
      val modelProbe = createTestProbe[ModelCommand]()
      val monitorProbe = createTestProbe[MonitorCommand]()
      val trainerProbe = createTestProbe[TrainerCommand]()

      val gossipActor = spawn(GossipActor(modelProbe.ref, monitorProbe.ref, trainerProbe.ref))
      val remoteModel = Model(Network(List.empty), List.empty)

      // Inviamo un modello remoto al GossipActor
      gossipActor ! GossipCommand.HandleRemoteModel(remoteModel)

      // Verifichiamo che venga inviato il comando di Sync al ModelActor locale
      modelProbe.expectMessage(ModelActor.ModelCommand.SyncModel(remoteModel))
    }

    "broadcast control commands to all peers when receiving SpreadCommand" in {
      val modelProbe = createTestProbe[ModelCommand]()
      val monitorProbe = createTestProbe[MonitorCommand]()
      val trainerProbe = createTestProbe[TrainerCommand]()
      val peer1 = createTestProbe[GossipCommand]()
      val peer2 = createTestProbe[GossipCommand]()

      val gossipActor = spawn(GossipActor(modelProbe.ref, monitorProbe.ref, trainerProbe.ref))

      // Aggiorniamo i peer
      gossipActor ! GossipCommand.UpdatePeers(Set(peer1.ref, peer2.ref))

      // Chiediamo di diffondere la pausa
      gossipActor ! GossipCommand.SpreadCommand(ControlCommand.GlobalPause)

      // Entrambi i peer devono ricevere il comando
      peer1.expectMessage(GossipCommand.HandleControlCommand(ControlCommand.GlobalPause))
      peer2.expectMessage(GossipCommand.HandleControlCommand(ControlCommand.GlobalPause))
    }

    "execute local actions (Pause/Resume/Stop) when receiving HandleControlCommand" in {
      val monitorProbe = createTestProbe[MonitorCommand]()
      val trainerProbe = createTestProbe[TrainerCommand]()
      val modelProbe = createTestProbe[ModelCommand]()

      val gossipActor = spawn(GossipActor(modelProbe.ref, monitorProbe.ref, trainerProbe.ref))

      // Caso GlobalPause
      gossipActor ! GossipCommand.HandleControlCommand(ControlCommand.GlobalPause)
      monitorProbe.expectMessage(MonitorCommand.InternalPause)
      trainerProbe.expectMessage(TrainerCommand.Pause)

      // Caso GlobalResume
      gossipActor ! GossipCommand.HandleControlCommand(ControlCommand.GlobalResume)
      monitorProbe.expectMessage(MonitorCommand.InternalResume)
      trainerProbe.expectMessage(TrainerCommand.Resume)
    }
  }
}
