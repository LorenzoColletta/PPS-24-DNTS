package actors.root

import actors.cluster.ClusterProtocol.RegisterMonitor
import actors.cluster.timer.ClusterTimers
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import config.{AppConfig, ConfigLoader, FileConfig}
import domain.network.{Activations, Feature, Model, ModelBuilder}
import domain.dataset.*
import domain.training.{LossFunction, Optimizer}
import domain.training.Strategies.{Optimizers, Regularizers}
import actors.monitor.MonitorActor
import actors.monitor.MonitorActor.MonitorCommand
import actors.cluster.{ClusterManager, ClusterProtocol, ClusterState}
import actors.discovery.{DiscoveryActor, GossipPeerState}
import actors.gossip.GossipActor
import actors.gossip.GossipProtocol.GossipCommand
import actors.model.ModelActor
import actors.model.ModelProtocol.ModelCommand
import actors.root.RootProtocol.{NodeRole, RootCommand}
import actors.trainer.{TrainerActor, TrainerProtocol}
import actors.trainer.TrainerActor.TrainingConfig
import com.typesafe.config.Config
import domain.pattern.SpiralCurve
import domain.util.Space
import view.*

/**
 * Encapsulates the behavior logic for the TrainerActor.
 *
 * @param context     The actor context providing access to the actor system.
 * @param role        The specific role of this node.
 * @param configPath  Optional file path to the configuration file used.
 * @param appConfig   Implicit global application configuration.
 */
class RootBehavior(context: ActorContext[RootCommand],
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

    given LossFunction = appConfig.lossFunction

    val modelActor = context.spawn(ModelActor(), "modelActor")
    val trainerActor = context.spawn(TrainerActor(modelActor), "trainerActor")
    val guiView = GuiView()

    val discoveryActor = context.spawn(DiscoveryActor(GossipPeerState.empty), "discoveryActor")

    val clusterManager = context.spawn(
      ClusterManager(
        ClusterState.initialState(role),
        ClusterTimers.fromConfig(akkaConfig),
        None,
        discoveryActor,
        context.self
      ),
      "clusterManager")

    val gossipActor = context.spawn(GossipActor(modelActor, trainerActor, clusterManager, discoveryActor ), "gossipActor")

    val monitorActor = context.spawn(
      MonitorActor(
        modelActor,
        gossipActor,
        context.self,
        guiView,
        isMaster = (role == NodeRole.Seed)
      ),
      "monitorActor"
    )

    clusterManager ! ClusterProtocol.RegisterMonitor(monitorActor)

    seedDataPayload.foreach { case (model, _, trainConfig, optimizer, _) =>
      modelActor ! ModelCommand.Initialize(model, optimizer, trainerActor)

      monitorActor ! MonitorCommand.Initialize(
        seed = "LocalMaster",
        model = model,
        config = trainConfig
      )
    }

    val (startModel, startData, startConf) = seedDataPayload.map(p =>
      (Some(p._1), Some(p._2), Some(p._5))
    ).getOrElse((None, None, None))
    waitingForStart(gossipActor, startModel, startData, startConf)


  /**
   * State: Waiting for the Seed Start Simulation command.
   */
  private def waitingForStart(
                               gossipActor: ActorRef[GossipActor.GossipCommand],
                               model: Option[Model],
                               dataset: Option[List[domain.data.LabeledPoint2D]],
                               fileConfig: Option[FileConfig]
                             ): Behavior[RootCommand] =

    Behaviors.receive: (ctx, msg) =>
      msg match
        case RootCommand.SeedStartSimulation =>
          (role, model, dataset, fileConfig) match
            case (NodeRole.Seed, Some(m), Some(d), Some(conf)) =>
              context.log.info("Root: Received Start Command. Distributing Data and Model to Cluster...")

              val trainSize = (d.size * (1.0 - conf.testSplit)).toInt
              val (globalTrain, globalTest) = d.splitAt(trainSize)

              context.log.info(s"Root: Data Split - Train: ${globalTrain.size}, Test: ${globalTest.size}")

              gossipActor ! GossipCommand.DistributeDataset(globalTrain, globalTest)
              Behaviors.same

            case _ =>
              context.log.warn("Root: Received Start command but I am a Worker or Data is missing.")
              Behaviors.same


  private def createModel(conf: FileConfig): Model =
    var builder = ModelBuilder.fromInputs(conf.features *)
    conf.networkLayers.foreach(l => builder = builder.addLayer(l.neurons, l.activation))
    conf.seed.foreach(s => builder = builder.withSeed(s))
    builder.build()

  private def generateDataset(conf: FileConfig): List[domain.data.LabeledPoint2D] =
    val seedPos = conf.seed

    given Space = appConfig.space

    val datasetModel = DataModelFactory.create(conf.datasetConf, seedPos)

    val data = DatasetGenerator.generate(conf.datasetSize, datasetModel)
    context.log.info(s"Root: Generated Global Dataset with ${data.size} samples.")
    data

  private def createTrainConfig(conf: FileConfig): TrainerProtocol.TrainingConfig =
    TrainerProtocol.TrainingConfig(
      trainSet = Nil,
      testSet = Nil,
      features = conf.features,
      hp = conf.hyperParams,
      epochs = conf.epochs,
      batchSize = conf.batchSize,
      seed = conf.seed
    )
    
