package shape.komputation.demos.and

import shape.komputation.cpu.loss.squaredLoss
import shape.komputation.initialization.heInitialization
import shape.komputation.layers.entry.inputLayer
import shape.komputation.layers.forward.activation.sigmoidLayer
import shape.komputation.layers.forward.projection.projectionLayer
import shape.komputation.matrix.Matrix
import shape.komputation.matrix.doubleColumnVector
import shape.komputation.matrix.doubleScalar
import shape.komputation.networks.Network
import shape.komputation.networks.printLoss
import shape.komputation.optimization.stochasticGradientDescent
import java.util.*

fun main(args: Array<String>) {

    val inputs = arrayOf<Matrix>(
        doubleColumnVector(0.0, 0.0),
        doubleColumnVector(1.0, 0.0),
        doubleColumnVector(0.0, 1.0),
        doubleColumnVector(1.0, 1.0))

    val targets = arrayOf(
        doubleScalar(0.0),
        doubleScalar(0.0),
        doubleScalar(0.0),
        doubleScalar(1.0)
    )

    val random = Random(1)

    val initialize = heInitialization(random)
    val optimizer = stochasticGradientDescent(0.03)

    val projectionLayer = projectionLayer(2, 1, initialize, initialize, optimizer)

    val network = Network(
        inputLayer(),
        projectionLayer,
        sigmoidLayer(1)
    )

    network.train(inputs, targets, squaredLoss(), 10_000, 1, printLoss)

}