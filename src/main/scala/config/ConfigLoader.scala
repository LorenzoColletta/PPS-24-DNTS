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
    val file = new File(path)
    val conf = 
      if (file.exists()) ConfigFactory.parseFile(file).resolve() 
      else ConfigFactory.load(path).resolve()

    val sim = conf.getConfig("simulation")

    val seed = if sim.hasPath("seed") then Some(sim.getLong("seed")) else None

    val features = sim.getStringList("network.features").asScala
      .map(f => Feature.valueOf(f)).toList

    val layers = sim.getConfigList("network.hidden-layers").asScala.map { c =>
      val actName = c.getString("activation").toLowerCase
      val activation = Activations.registry.getOrElse(
        actName,
        throw new IllegalArgumentException(s"Unknown activation: $actName")
      )
      LayerConf(c.getInt("neurons"), activation)
    }.toList

    val trainConf = sim.getConfig("training")
    val hpConf = trainConf.getConfig("hyper-params")
    val regConf = hpConf.getConfig("regularization")

    val regType = regConf.getString("type")
    val regularization = Regularization.fromName(
      regType,
      key => regConf.getDouble(key)
    )

    val hp = HyperParams(hpConf.getDouble("learning-rate"), regularization)

    FileConfig(
      seed = seed,
      networkLayers = layers,
      features = features,
      datasetType = sim.getConfig("dataset").getString("type"),
      datasetSize = sim.getConfig("dataset").getInt("size"),
      testSplit = sim.getConfig("dataset").getDouble("test-split"),
      epochs = trainConf.getInt("epochs"),
      batchSize = trainConf.getInt("batch-size"),
      hyperParams = hp,
    )
