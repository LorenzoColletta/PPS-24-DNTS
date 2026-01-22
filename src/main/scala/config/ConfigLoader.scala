package config

import com.typesafe.config.ConfigFactory
import domain.network.{Activations, Feature, Regularization, HyperParams, LayerConf}
import java.io.File
import scala.jdk.CollectionConverters._


/**
 * Immutable configuration container representing a complete simulation setup.
 * This class acts as a Data Transfer Object (DTO) mapped from a HOCON configuration file.
 *
 * @param seed          The optional random seed used for reproducibility of weight initialization and data shuffling.
 * @param networkLayers The topology of the neural network (hidden layers and their activation functions).
 * @param features      The list of input features enabled for the model.
 * @param datasetType   The name or type of the dataset to generate (e.g., "xor", "spiral").
 * @param datasetConf   The specific configuration parameters for the chosen dataset type.
 * @param datasetSize   The total number of samples to generate.
 * @param testSplit     The fraction of the dataset to set aside for validation (e.g., 0.2 for 20%).
 * @param epochs        The number of training iterations over the dataset.
 * @param batchSize     The number of samples processed in a single training step.
 * @param hyperParams   Training hyperparameters including learning rate and regularization strategy.
 */
case class FileConfig(
  seed: Option[Long],
  networkLayers: List[LayerConf],
  features: List[Feature],

  datasetType: String,
  datasetConf: DatasetStrategyConfig,
  datasetSize: Int,
  testSplit: Double,

  epochs: Int,
  batchSize: Int,
  hyperParams: HyperParams
) {
  require(!seed.contains(0), "Seed cannot be zero (if provided).")

  require(networkLayers.nonEmpty, "Neural Network must have at least one hidden layer.")

  require(features.nonEmpty, "At least one input feature must be selected.")

  require(datasetSize > 0, s"Dataset size must be positive. Found: $datasetSize")

  require(testSplit >= 0.0 && testSplit < 1.0, s"Test split must be >= 0.0 and < 1.0. Found: $testSplit")

  require(epochs > 0, s"Epochs must be positive. Found: $epochs")

  require(batchSize > 0, s"Batch size must be positive. Found: $batchSize")
}


/**
 * Utility object responsible for parsing configuration files into domain objects.
 * It utilizes the Typesafe Config library to read HOCON (Human-Optimized Config Object Notation) files.
 */
object ConfigLoader:

  /**
   * Loads and parses a configuration file from the specified path.
   *
   * This method attempts to locate the configuration in the following order:
   * 1. As a physical file on the filesystem.
   * 2. As a resource on the classpath (if the physical file does not exist).
   *
   *
   * @param path The path to the configuration file or the name of the resource.
   * @return A [[FileConfig]] instance containing the parsed simulation settings.
   * @throws IllegalArgumentException If an unknown activation function or regularization type is encountered.
   */
  def load(path: String): FileConfig =
    val rootConf = resolveConfig(path)
    val simConf = rootConf.getConfig("simulation")

    val seed = if simConf.hasPath("seed") then Some(simConf.getLong("seed")) else None
    
    val (layers, features) = parseNetwork(simConf.getConfig("network"))

    val (datasetStrategy, dsType, dsSize, testSplit) = parseDataset(simConf.getConfig("dataset"))

    val (epochs, batchSize, hp) = parseTraining(simConf.getConfig("training"))

    FileConfig(
      seed = seed,
      networkLayers = layers,
      features = features,
      datasetConf = datasetStrategy,
      datasetType = dsType,
      datasetSize = dsSize,
      testSplit = testSplit,
      epochs = epochs,
      batchSize = batchSize,
      hyperParams = hp
    )


  private def resolveConfig(path: String): Config =
    val file = new File(path)
    if file.exists()
      then ConfigFactory.parseFile(file).resolve()
      else ConfigFactory.load(path).resolve()

  private def parseNetwork(netConf: Config): (List[LayerConf], List[Feature]) =
    val features = netConf.getStringList("features").asScala
      .map(f => Feature.valueOf(f)).toList

    val layers = netConf.getConfigList("hidden-layers").asScala.map { c =>
      val actName = c.getString("activation").toLowerCase
      val activation = Activations.registry.getOrElse(
        actName,
        throw new IllegalArgumentException(s"Unknown activation: $actName")
      )
      LayerConf(c.getInt("neurons"), activation)
    }.toList

    (seed, layers, features)

  private def parseDataset(dsConf: Config): (DatasetStrategyConfig, String, Int, Double) =
    val typeName = dsConf.getString("type")
    val size = dsConf.getInt("size")
    val split = dsConf.getDouble("test-split")

    val strategy = parseDatasetStrategy(typeName, dsConf)

    (strategy, typeName, size, split)

  private def parseTraining(trainConf: Config): (Int, Int, HyperParams) =
    val epochs = trainConf.getInt("epochs")
    val batchSize = trainConf.getInt("batch-size")
    val hpConf = trainConf.getConfig("hyper-params")
    
    val regConf = hpConf.getConfig("regularization")
    val regularization = Regularization.fromName(
      regConf.getString("type"),
      key => regConf.getDouble(key)
    )

    val hp = HyperParams(hpConf.getDouble("learning-rate"), regularization)

    (epochs, batchSize, hp)

  private def parseDatasetStrategy(typeName: String, conf: Config): DatasetStrategyConfig =
    typeName.toLowerCase match
      case "gaussian" =>
        Gaussian(
          distance = conf.getDouble("distance"),
          sigma = conf.getDouble("sigma"),
          radius = conf.getDouble("radius"),
          domainMin = conf.getDouble("domain-min"),
          domainMax = conf.getDouble("domain-max")
        )

      case "ring" =>
        Ring(
          centerX = conf.getDouble("center-x"),
          centerY = conf.getDouble("center-y"),
          innerRadius = conf.getDouble("inner-radius"),
          outerRadius = conf.getDouble("outer-radius"),
          thickness = conf.getDouble("thickness"),
          domainMin = conf.getDouble("domain-min"),
          domainMax = conf.getDouble("domain-max")
        )

      case "spiral" =>
        Spiral(
          startDistance = conf.getDouble("start-distance"),
          branchDistance = conf.getDouble("branch-distance"),
          rotation = conf.getDouble("rotation")
        )

      case "xor" =>
        Xor(
          domainMin = conf.getDouble("domain-min"),
          domainMax = conf.getDouble("domain-max")
        )

      case other =>
        throw new IllegalArgumentException(s"Unknown dataset type: $other")
