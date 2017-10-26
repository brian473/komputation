package com.komputation.cpu.loss

import com.komputation.matrix.FloatMath

class CpuLogisticLoss(override val numberInputColumns: Int) : CpuLossFunction {

    override val numberInputRows = 1

    private val numberInputEntries = this.numberInputColumns

    override val backwardResult = FloatArray(this.numberInputEntries)

    override fun forward(predictions: FloatArray, targets : FloatArray): Float {

        var loss = 0.0f

        for (index in 0 until this.numberInputEntries) {

            val target = targets[index]

            val prediction = predictions[index]

            if (target == 1.0f) {

                // -log(probability)
                loss += -FloatMath.log(prediction)

            }
            else{

                // -log(1 - probability)
                val counterProbability = 1.0f.minus(prediction)

                loss += -FloatMath.log(counterProbability)

            }

        }

        return loss

    }

    override fun backward(predictions: FloatArray, targets : FloatArray): FloatArray {

        for(indexEntry in 0 until this.numberInputEntries) {

            val prediction = predictions[indexEntry]

            if (targets[indexEntry] == 1.0f) {

                this.backwardResult[indexEntry] = (-1.0f).div(prediction)

            }
            else {

                this.backwardResult[indexEntry] = 1.0f.div(1.0f - prediction)

            }

        }

        return this.backwardResult

    }

}