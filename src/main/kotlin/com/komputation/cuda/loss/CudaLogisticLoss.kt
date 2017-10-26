package com.komputation.cuda.loss

import com.komputation.cuda.allocateDeviceFloatMemory
import com.komputation.cuda.computeDeviceFloatArraySize
import com.komputation.cuda.getFloatArray
import com.komputation.cuda.kernels.Kernel
import com.komputation.cuda.kernels.launch.computeEntrywiseLaunchConfiguration
import com.komputation.cuda.kernels.launch.computeRowwiseLaunchConfiguration
import jcuda.Pointer
import jcuda.runtime.JCuda.cudaFree

class CudaLogisticLoss internal constructor(
    private val numberSteps : Int,
    private val createForwardKernel : () -> Kernel,
    private val createBackwardKernel : () -> Kernel,
    private val numberMultiprocessors : Int,
    private val numberResidentWarps: Int,
    private val warpSize: Int,
    private val maximumNumberThreadsPerBlock : Int) : CudaLossFunction {

    private val pointerToNumberSteps = Pointer.to(intArrayOf(this.numberSteps))

    private var forwardKernel : Kernel? = null

    private val deviceForwardResult = Pointer()
    private val pointerToDeviceForwardResult = Pointer.to(this.deviceForwardResult)

    private var maximumBatchSize = -1

    private val forwardBatchSize = intArrayOf(-1)
    private val pointerToForwardBatchSize = Pointer.to(this.forwardBatchSize)

    private var forwardNumberBlocksInYDimension = -1
    private var forwardNumberThreadsPerBlock = -1
    private val forwardNumberIterations = intArrayOf(-1)
    private val pointerToForwardNumberIterations = Pointer.to(this.forwardNumberIterations)
    private var forwardSharedMemoryBytes = -1

    private var backwardKernel : Kernel? = null

    private val deviceBackwardResult = Pointer()
    private val pointerToBackwardResult = Pointer.to(this.deviceBackwardResult)

    private val backwardBatchSize = intArrayOf(-1)
    private val pointerToBackwardBatchSize = Pointer.to(this.backwardBatchSize)

    private var backwardNumberBlocksInYDimension = -1
    private var backwardNumberThreadsPerBlock = -1
    private val backwardNumberIterations = intArrayOf(-1)
    private val pointerToBackwardNumberIterations = Pointer.to(this.backwardNumberIterations)

    override fun acquire(maximumBatchSize : Int) {

        this.maximumBatchSize = maximumBatchSize

        allocateDeviceFloatMemory(this.deviceForwardResult, maximumBatchSize * this.numberSteps)

        val forwardLaunchConfiguration = computeRowwiseLaunchConfiguration(1, this.numberSteps, this.warpSize, this.maximumNumberThreadsPerBlock)
        this.forwardNumberBlocksInYDimension = forwardLaunchConfiguration.numberBlocks
        this.forwardNumberThreadsPerBlock = forwardLaunchConfiguration.numberThreadsPerBlock
        this.forwardNumberIterations[0] = forwardLaunchConfiguration.numberIterations
        this.forwardKernel = this.createForwardKernel()
        val numberForwardWarps = (this.numberSteps / forwardLaunchConfiguration.numberIterations + this.warpSize - 1) / this.warpSize
        this.forwardSharedMemoryBytes = computeDeviceFloatArraySize(numberForwardWarps).toInt()

        allocateDeviceFloatMemory(this.deviceBackwardResult, maximumBatchSize * this.numberSteps)

        val backwardLaunchConfiguration = computeEntrywiseLaunchConfiguration(this.numberSteps, this.numberMultiprocessors, this.numberResidentWarps, this.warpSize, this.maximumNumberThreadsPerBlock)
        this.backwardNumberBlocksInYDimension = backwardLaunchConfiguration.numberBlocks
        this.backwardNumberThreadsPerBlock = backwardLaunchConfiguration.numberThreadsPerBlock
        this.backwardNumberIterations[0] = backwardLaunchConfiguration.numberIterations
        this.backwardKernel = this.createBackwardKernel()

    }

    override fun accumulate(pointerToPredictions: Pointer, pointerToTargets: Pointer, batchSize: Int) {

        this.forwardBatchSize[0] = batchSize

        val parameters = Pointer.to(
            this.pointerToForwardBatchSize,
            this.pointerToNumberSteps,
            this.pointerToForwardNumberIterations,
            pointerToPredictions,
            pointerToTargets,
            this.pointerToDeviceForwardResult
        )

        this.forwardKernel!!.launch(
            parameters,
            batchSize,
            this.forwardNumberBlocksInYDimension,
            this.forwardNumberThreadsPerBlock,
            this.forwardSharedMemoryBytes)

    }

    override fun accessAccumulation(): Float {

        val sums = getFloatArray(this.deviceForwardResult, this.maximumBatchSize * this.numberSteps)
        val loss = sums.sum()

        return loss

    }

    override fun backward(pointerToPredictions: Pointer, pointerToTargets: Pointer, batchSize : Int): Pointer {

        this.backwardBatchSize[0] = batchSize

        val parameters = Pointer.to(
            this.pointerToBackwardBatchSize,
            this.pointerToNumberSteps,
            this.pointerToBackwardNumberIterations,
            pointerToPredictions,
            pointerToTargets,
            this.pointerToBackwardResult
        )

        this.backwardKernel!!.launch(
            parameters,
            this.maximumBatchSize,
            this.backwardNumberBlocksInYDimension,
            this.backwardNumberThreadsPerBlock,
            0)

        return this.deviceBackwardResult

    }

    override fun release() {

        this.forwardKernel!!.destroy()

        cudaFree(this.deviceForwardResult)

        this.backwardKernel!!.destroy()

        cudaFree(this.deviceBackwardResult)

    }

}