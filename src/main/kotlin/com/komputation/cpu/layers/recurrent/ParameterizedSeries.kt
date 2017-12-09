package com.komputation.cpu.layers.recurrent

import com.komputation.cpu.layers.CpuForwardLayer
import com.komputation.cpu.optimization.DenseAccumulator
import com.komputation.cpu.optimization.UpdateRule
import com.komputation.cpu.optimization.updateDensely
import com.komputation.optimization.Optimizable

class ParameterizedSeries internal constructor(
    name : String?,
    steps: Array<CpuForwardLayer>,
    private val sharedParameter: FloatArray,
    private val seriesAccumulator: DenseAccumulator,
    private val batchAccumulator: DenseAccumulator,
    private val updateRule: UpdateRule? = null) : Series(name, steps), Optimizable {

    private val numberEntries = sharedParameter.size

    fun backwardSeries() {
        this.batchAccumulator.accumulate(this.seriesAccumulator.getAccumulation())
        this.seriesAccumulator.reset()
    }

    override fun optimize(batchSize : Int) {
        if (this.updateRule != null) {
            updateDensely(this.sharedParameter, this.batchAccumulator.getAccumulation(), this.numberEntries, batchSize, this.updateRule)
        }

        this.batchAccumulator.reset()
    }

}