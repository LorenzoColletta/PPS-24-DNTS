package domain.serialization

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import scala.util.{Try, Failure, Success}

import domain.network.{Network, Layer, Activation}
import domain.data.LinearAlgebra.{Matrix, Vector}

/**
 * Binary serializers for Neural [[Network]]s.
 */
object NetworkSerializers:

  /**
   * Serializer for the complete Neural [[Network]] topology.
   * Handles layer iteration and delegates weight/bias serialization to implicit instances.
   *
   * @param mSer                The implicit [[Serializer]] used for [[Matrix]] serialization.
   * @param vSer                The implicit [[Serializer]] used for [[Vector]] serialization.
   * @param activationRegistry  The implicit mapping from the string name to the [[Activation]] instance.
   */
  given networkSerializer(
    using
      mSer: Serializer[Matrix],
      vSer: Serializer[Vector],
      activationRegistry: Map[String, Activation]
  ): Serializer[Network] with

    def serialize(net: Network): Array[Byte] =
      val layersData = net.layers.map { layer =>
        val nameBytes = layer.activation.name.toLowerCase.getBytes(StandardCharsets.UTF_8)
        val wBytes = mSer.serialize(layer.weights)
        val bBytes = vSer.serialize(layer.biases)
        (nameBytes, wBytes, bBytes)
      }

      val totalSize = 4 + layersData.map { case (n, w, b) =>
        4 + n.length + 4 + w.length + 4 + b.length
      }.sum

      val buffer = ByteBuffer.allocate(totalSize)
      buffer.putInt(net.layers.length)

      layersData.foreach { case (n, w, b) =>
        buffer.putInt(n.length); buffer.put(n)
        buffer.putInt(w.length); buffer.put(w)
        buffer.putInt(b.length); buffer.put(b)
      }
      buffer.array()

    def deserialize(bytes: Array[Byte]): Try[Network] = Try {
      val buffer = ByteBuffer.wrap(bytes)
      val numLayers = buffer.getInt

      val layers = (0 until numLayers).map { _ =>
        val nameLen = buffer.getInt
        val nameBytes = new Array[Byte](nameLen)
        buffer.get(nameBytes)
        val actName = new String(nameBytes, StandardCharsets.UTF_8)

        val activation = activationRegistry.getOrElse(actName.toLowerCase,
          throw new IllegalArgumentException(
            s"Activation '$actName' not found in registry"
          )
        )

        val wLen = buffer.getInt
        val wBytes = new Array[Byte](wLen)
        buffer.get(wBytes)
        val weights = mSer.deserialize(wBytes).get

        val bLen = buffer.getInt
        val bBytes = new Array[Byte](bLen)
        buffer.get(bBytes)
        val biases = vSer.deserialize(bBytes).get

        Layer(weights, biases, activation)
      }.toList

      Network(layers)
    }
