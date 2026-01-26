package domain.serialization

import domain.network.Network
import domain.data.LinearAlgebra.{Matrix, Vector}

object Exporters:

  /**
   * Exporter for generating detailed JSON representations of a [[Network]].
   */
  given networkExporter: Exporter[Network] with
    def jsonExport(net: Network): String =
      val layersJson = net.layers.zipWithIndex.map { case (layer, idx) =>

        val neurons = layer.weights.rows
        val inputSize = layer.weights.cols

        val biasString = layer.biases.toList.mkString("[", ", ", "]")

        val weightsFlat = layer.weights.toFlatList
        val weightsByRow = weightsFlat.grouped(inputSize).toList

        val weightsString = weightsByRow.map { row =>
          row.mkString("        [", ", ", "]")
        }.mkString("[\n", ",\n", "\n      ]")

        s"""    {
           |      "layer_index": $idx,
           |      "activation": "${layer.activation.name}",
           |      "input_size": $inputSize,
           |      "neurons_count": $neurons,
           |      "parameters": {
           |        "biases": $biasString,
           |        "weights": $weightsString
           |      }
           |    }""".stripMargin
      }.mkString(",\n")

      s"""{
         |  "type": "DeepNeuralNetwork",
         |  "total_layers": ${net.layers.length},
         |  "architecture": [
         |$layersJson
         |  ]
         |}""".stripMargin
