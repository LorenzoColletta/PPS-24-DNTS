package config

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets

class ConfigLoaderTest extends AnyFunSuite with Matchers:

  test("ConfigLoader should correctly identify and load a Spiral strategy configuration"):
    val hocon =
      """
        |simulation {
        |  seed = 42
        |
        |  network { 
        |   features = ["X"], 
        |   hidden-layers = [
        |      { neurons: 8, activation: "Relu" },
        |      { neurons: 4, activation: "Tanh" }
        |    ]
        |  }
        |
        |  dataset {
        |    type = "Spiral"
        |    size = 100
        |    test-split = 0.2
        |    start-distance = 0.5
        |    branch-distance = 0.2
        |    rotation = 0.0
        |  }
        |
        |  training { 
        |    epochs=1, 
        |    batch-size=1, 
        |    hyper-params { 
        |      learning-rate=0.1, 
        |      regularization { 
        |        type = "L2"
        |        rate = 0.01
        |      }
        |    }
        |  }
        |}
        |""".stripMargin

    val tempFile = Files.createTempFile("test_spiral", ".conf")
    Files.write(tempFile, hocon.getBytes(StandardCharsets.UTF_8))

    try
      val config = ConfigLoader.load(tempFile.toAbsolutePath.toString)
      
      config.datasetConf shouldBe a [DatasetStrategyConfig.Spiral]
    finally
      Files.deleteIfExists(tempFile)
  

  test("ConfigLoader should correctly identify and load a Gaussian strategy configuration"):
    val hocon =
      """
        |simulation {
        |  seed = 42
        |  
        |  network { 
        |   features = ["X"], 
        |   hidden-layers = [
        |      { neurons: 8, activation: "Relu" },
        |      { neurons: 4, activation: "Tanh" }
        |    ]
        |  }
        |
        |  dataset {
        |    type = "Gaussian"
        |    size = 100
        |    test-split = 0.1
        |    distance = 2.0
        |    sigma = 0.5
        |    radius = 1.0
        |    domain-min = -5
        |    domain-max = 5
        |  }
        |  
        |  training { 
        |    epochs=1, 
        |    batch-size=1, 
        |    hyper-params { 
        |      learning-rate=0.1, 
        |      regularization { 
        |        type = "ElasticNet"
        |        l1_rate = 0.005
        |        l2_rate = 0.005
        |      }
        |    }
        |  }
        |}
        |""".stripMargin

    val tempFile = Files.createTempFile("test_gaussian", ".conf")
    Files.write(tempFile, hocon.getBytes(StandardCharsets.UTF_8))

    try
      val config = ConfigLoader.load(tempFile.toAbsolutePath.toString)

      config.datasetConf shouldBe a [DatasetStrategyConfig.Gaussian]
    finally
      Files.deleteIfExists(tempFile)
      