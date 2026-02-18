package actors.root

import actors.cluster.ClusterProtocol.{ClusterMemberCommand, RegisterMonitor}
import actors.cluster.timer.ClusterTimers
import akka.actor.typed.{ActorRef, Behavior, Terminated}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import config.{AppConfig, ConfigLoader, FileConfig}
import domain.network.{Feature, Model, ModelBuilder}
import domain.training.LossFunction
import domain.training.Strategies.{Optimizers, Regularizers}
import actors.monitor.MonitorActor
import actors.monitor.MonitorActor.MonitorCommand
import actors.cluster.{ClusterManager, ClusterProtocol, ClusterState}
import actors.discovery.{DiscoveryActor, DiscoveryProtocol, GossipPeerState}
import actors.gossip.GossipActor.GossipCommand
import actors.gossip.GossipActor
import actors.gossip.GossipProtocol.GossipCommand
import actors.model.ModelActor
import actors.model.ModelActor.ModelCommand
import actors.root.RootProtocol.{NodeRole, RootCommand}
import actors.trainer.TrainerActor
import actors.trainer.TrainerActor.TrainerCommand
import actors.trainer.TrainerActor.TrainingConfig
import actors.discovery.DiscoveryProtocol.DiscoveryCommand
import actors.gossip.GossipActor.ControlCommand.GlobalStop
import domain.data.LabeledPoint2D
import com.typesafe.config.Config
import domain.data.dataset.{DataModelFactory, DatasetGenerator, shuffle}
import domain.data.util.Space
import view.*

/**
 * Encapsulates the behavior logic for the TrainerActor.
 *
 * @param context     The actor context providing access to the actor system.
 * @param role        The specific role of this node.
 * @param configPath  Optional file path to the configuration file used.
 * @param appConfig   Implicit global application configuration.
 */
class RootBehavior(
  context: ActorContext[RootCommand],
  role: NodeRole,
  configPath: Option[String],
  akkaConfig: Config
)(using appConfig: AppConfig):

  /**
   * Bootstrap logic: executed immediately upon creation.
   */
  def start(): Behavior[RootCommand] =
    context.log.info(s"Root: Bootstrapping system with role $role...")

    val seedDataPayload = role match
      case NodeRole.Seed =>
        val path = configPath.getOrElse("simulation.conf")
        val fileConf = ConfigLoader.load(path)
        context.log.info(s"Root: Configuration loaded from $path")
    
        val model = createModel(fileConf)
        val data = generateDataset(fileConf)
        val tConfig = createTrainConfig(fileConf)
    
        val optimizer = new Optimizers.SGD(
          fileConf.hyperParams.learningRate,
          Regularizers.fromConfig(fileConf.hyperParams.regularization)
        )
    
        Some((model, data, tConfig, optimizer, fileConf))

      case NodeRole.Client =>
        context.log.info("Root: Client node started. Waiting for cluster configuration...")
        None

    val discoveryActor = context.spawn(DiscoveryActor(GossipPeerState.empty), "discoveryActor")
    context.watch(discoveryActor)
    
    val clusterManager = context.spawn(
      ClusterManager(
        ClusterState.initialState(role),
        ClusterTimers.fromConfig(akkaConfig.getConfig("akka")),
        None,
        discoveryActor,
        context.self
      ),
      "clusterManager")
    context.watch(clusterManager)

    val guiView = GuiView()

    val modelActor = context.spawn(ModelActor(), "modelActor")
    context.watch(modelActor)

    given LossFunction = appConfig.lossFunction

    val trainerActor = context.spawn(TrainerActor(modelActor), "trainerActor")
    context.watch(trainerActor)

    val gossipActor = context.spawn(GossipActor(context.self, modelActor, trainerActor, discoveryActor), "gossipActor")
    context.watch(gossipActor)
    
    val monitorActor = context.spawn(
      MonitorActor(
        modelActor,
        gossipActor,
        context.self,
        guiView,
        isMaster = role == NodeRole.Seed
      ),
      "monitorActor"
    )
    context.watch(monitorActor)

    trainerActor ! TrainerCommand.RegisterServices(monitorActor, gossipActor)
    clusterManager ! ClusterProtocol.RegisterMonitor(monitorActor)

    gossipActor ! GossipCommand.StartTickRequest

    waitingForStart(seedDataPayload, gossipActor, modelActor, trainerActor, monitorActor, clusterManager, discoveryActor, guiView)

  /**
   * State: Waiting for the Seed Start Simulation command.
   */
  private def waitingForStart(
    seedDataPayload: Option[(Model, List[LabeledPoint2D], TrainingConfig, Optimizers.SGD, FileConfig)],
    gossipActor: ActorRef[GossipCommand],
    modelActor: ActorRef[ModelCommand],
    trainerActor: ActorRef[TrainerCommand],
    monitorActor: ActorRef[MonitorCommand],
    clusterManager: ActorRef[ClusterMemberCommand],
    discoveryActor: ActorRef[DiscoveryCommand],
    guiView: GuiView
  ): Behavior[RootCommand] =

    Behaviors.receive: (ctx, msg) =>
      msg match
        case RootCommand.ConfirmInitialConfiguration(seedID, model, trainConfig) =>
          val regularizationStrategy = Regularizers.fromConfig(trainConfig.hp.regularization)
          val optimizer = Optimizers.SGD(trainConfig.hp.learningRate, regularizationStrategy)
          modelActor ! ModelCommand.Initialize(model, optimizer, trainerActor)
          monitorActor ! MonitorCommand.Initialize(seedID, model, trainConfig)
          trainerActor ! TrainerCommand.SetTrainConfig(trainConfig)
          Behaviors.same

        case RootCommand.DistributedDataset(trainShard, testSet) =>
          clusterManager ! ClusterProtocol.StartSimulation
          trainerActor ! TrainerCommand.Start(trainShard, testSet)
          Behaviors.same

        case RootCommand.SeedStartSimulation =>
          val (model, dataset, fileConfig) = seedDataPayload.map(p =>
            (Some(p._1), Some(p._2), Some(p._5))
          ).getOrElse((None, None, None))

          (role, model, dataset, fileConfig) match
            case (NodeRole.Seed, Some(m), Some(d), Some(conf)) =>
              val trainSize = (d.size * (1.0 - conf.testSplit)).toInt
              val (globalTrain, globalTest) = d.splitAt(trainSize)

              context.log.info(s"Root: Data Split - Train: ${globalTrain.size}, Test: ${globalTest.size}")
              gossipActor ! GossipCommand.DistributeDataset(globalTrain, globalTest)
              Behaviors.same

            case _ =>
              context.log.warn("Root: Received Start command but I am a Worker or Data is missing.")
              Behaviors.same

        case RootCommand.ClusterReady =>
          val myAddress = ctx.system.address.toString
          context.log.info(s"Root: Node $role is now fully connected to the cluster.")

          seedDataPayload match
            case Some((model, _, trainConfig, optimizer, _)) =>
              modelActor   ! ModelCommand.Initialize(model, optimizer, trainerActor)
              monitorActor ! MonitorCommand.Initialize("LocalMaster", model, trainConfig)
              context.log.info("Root: Received Start Command. Distributing Data and Model to Cluster...")

              gossipActor ! GossipCommand.ShareConfig(myAddress, model, trainConfig)
              trainerActor ! TrainerCommand.SetTrainConfig(trainConfig)

            case None =>
              context.log.info(s"Root (CLIENT): Cluster Ready via $myAddress. Waiting for Seed Config...")
          
          Behaviors.same

        case RootCommand.ClusterFailed |
             RootCommand.SeedLost |
             RootCommand.InvalidCommandInBootstrap |
             RootCommand.InvalidCommandInJoining =>

          monitorActor ! MonitorCommand.ConnectionFailed(msg.toString)

          context.log.error("Root: Critical failure. Stopping actor.")
          Behaviors.same

        case RootCommand.StopSimulation =>
          clusterManager ! ClusterProtocol.StopSimulation
          trainerActor ! TrainerCommand.Stop
          monitorActor ! MonitorCommand.InternalStop
          modelActor ! ModelCommand.StopSimulation
          discoveryActor ! DiscoveryProtocol.Stop

          val children: Set[ActorRef[Nothing]] = Set(
            discoveryActor.unsafeUpcast,
            clusterManager.unsafeUpcast,
            modelActor.unsafeUpcast,
            trainerActor.unsafeUpcast,
            gossipActor.unsafeUpcast,
            monitorActor.unsafeUpcast
          )
          gracefullyStopping(children)

  private def gracefullyStopping(remainingActors: Set[ActorRef[Nothing]]): Behavior[RootCommand] =
    Behaviors.receiveSignal {
      case (ctx, Terminated(ref)) =>
        val stillAlive = remainingActors - ref
        ctx.log.info(s"Root: Actor ${ref.path.name} stopped. Remaining: ${stillAlive.size}")

        if stillAlive.isEmpty then
          ctx.log.info("Root: All children stopped. Shutting down system.")
          Behaviors.stopped
        else
          gracefullyStopping(stillAlive)
    }


  private def createModel(conf: FileConfig): Model =
    var builder = ModelBuilder.fromInputs(conf.features *)
    conf.networkLayers.foreach(l => builder = builder.addLayer(l.neurons, l.activation))
    conf.seed.foreach(s => builder = builder.withSeed(s))
    builder.build()

  private def generateDataset(conf: FileConfig): List[domain.data.LabeledPoint2D] =
    val seedPos = conf.seed

    given Space = appConfig.space

    val datasetModel = DataModelFactory.create(conf.datasetConf, seedPos)

    val data = DatasetGenerator.generate(conf.datasetSize, datasetModel).shuffle(seedPos)
    context.log.info(s"Root: Generated Global Dataset with ${data.size} samples.")
    data

  private def createTrainConfig(conf: FileConfig): TrainingConfig =
    TrainingConfig(
      trainSet = Nil,
      testSet = Nil,
      features = conf.features,
      hp = conf.hyperParams,
      epochs = conf.epochs,
      batchSize = conf.batchSize,
      seed = conf.seed
    )
    
