package com.komputation.cpu.layers

import com.komputation.layers.Resourceful

abstract class BaseCpuVariableLengthForwardLayer(
    private val name: String?,
    override val numberInputRows : Int,
    override val numberOutputRows : Int,
    protected val minimumColumns : Int,
    protected val maximumColumns : Int) : CpuForwardLayer, Resourceful {

    protected val numberPossibleLengths = this.maximumColumns - this.minimumColumns + 1
    protected val possibleLengths = IntArray(this.numberPossibleLengths) { index -> index + this.minimumColumns }
    protected var lengthIndex : Int = -1

    private var forwardResultsOverPossibleLengths = emptyArray<FloatArray>()
    override var forwardResult = FloatArray(0)

    private var backwardResultsOverPossibleLengths = emptyArray<FloatArray>()
    override var backwardResult = FloatArray(0)

    override var numberInputColumns = -1

    override var numberOutputColumns = -1
    private var numberOutputColumnsOverPossibleLengths = IntArray(0)

    override fun acquire(maximumBatchSize: Int) {
        this.numberOutputColumnsOverPossibleLengths = IntArray(this.numberPossibleLengths) { index -> computeNumberOutputColumns(index, this.possibleLengths[index]) }

        this.forwardResultsOverPossibleLengths = Array(this.numberPossibleLengths) { index -> FloatArray(this.numberOutputRows * this.numberOutputColumnsOverPossibleLengths[index]) }
        this.backwardResultsOverPossibleLengths = Array(this.numberPossibleLengths) { index -> FloatArray(this.numberInputRows * this.possibleLengths[index]) }
    }

    override fun release() {
    }

    protected abstract fun computeNumberOutputColumns(lengthIndex : Int, length : Int) : Int

    override fun forward(withinBatch : Int, numberInputColumns : Int, input: FloatArray, isTraining : Boolean) : FloatArray {
        this.lengthIndex = numberInputColumns - this.minimumColumns

        this.numberInputColumns = numberInputColumns
        this.numberOutputColumns = this.numberOutputColumnsOverPossibleLengths[this.lengthIndex]

        this.forwardResult = this.forwardResultsOverPossibleLengths[this.lengthIndex]

        this.computeForwardResult(withinBatch, numberInputColumns, input, isTraining, this.forwardResult)

        return this.forwardResult
    }

    protected abstract fun computeForwardResult(withinBatch : Int, numberInputColumns : Int, input : FloatArray, isTraining : Boolean, forwardResult: FloatArray)

    override fun backward(withinBatch : Int, chain : FloatArray) : FloatArray {
        this.backwardResult = this.backwardResultsOverPossibleLengths[this.lengthIndex]

        this.computeBackwardResult(withinBatch, this.forwardResult, chain, this.backwardResult)

        return this.backwardResult
    }

    protected abstract fun computeBackwardResult(withinBatch : Int, forwardResult : FloatArray, chain : FloatArray, backwardResult: FloatArray)

}