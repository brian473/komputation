package shape.komputation.cuda.layers.forward.dropout

import jcuda.Pointer
import jcuda.runtime.JCuda.cudaFree
import shape.komputation.cpu.functions.seed
import shape.komputation.cuda.allocateDeviceFloatMemory
import shape.komputation.cuda.kernels.Kernel
import shape.komputation.cuda.kernels.launch.computeEntrywiseLaunchConfiguration
import shape.komputation.cuda.layers.forward.activation.BaseCudaActivationLayer
import shape.komputation.cuda.setIntArray
import shape.komputation.layers.Resourceful
import java.util.*

class CudaDropoutLayer internal constructor(
    name : String? = null,
    numberRows: Int,
    numberColumns: Int,
    private val random: Random,
    keepProbability : Float,
    private val createTrainingKernel: () -> Kernel,
    private val createRuntimeKernel: () -> Kernel,
    private val createBackwardKernel: () -> Kernel,
    private val numberMultiprocessors : Int,
    private val numberResidentWarps : Int,
    private val warpSize : Int,
    private val maximumNumberThreadsPerBlock : Int) : BaseCudaActivationLayer(name), Resourceful {

    private val numberEntries = numberRows * numberColumns

    private var numberBlocksInXDimension = -1
    private var numberBlocksInYDimension = -1
    private var numberThreadsPerBlock = -1
    private var numberIterations = intArrayOf(-1)
    private val pointerToNumberIterations = Pointer.to(numberIterations)

    private val pointerToNumberEntries = Pointer.to(intArrayOf(this.numberEntries))
    private val pointerToKeepProbability = Pointer.to(floatArrayOf(keepProbability))
    private val pointerToDropoutProbability = Pointer.to(floatArrayOf(1.0f - keepProbability))

    private val deviceSeeds = Pointer()
    private val pointerToDeviceSeeds = Pointer.to(this.deviceSeeds)

    private val deviceMasks = Pointer()
    private val pointerToDeviceMasks = Pointer.to(this.deviceMasks)

    private var trainingKernel : Kernel? = null
    private var runtimeKernel : Kernel? = null
    override val deviceForwardResult = Pointer()
    override val numberOutputRows = numberRows
    override val maximumOutputColumns = numberColumns
    private val pointerToDeviceForwardResults = Pointer.to(this.deviceForwardResult)

    private var backwardKernel : Kernel? = null
    override val deviceBackwardResult = Pointer()
    override val numberInputRows = numberRows
    override val maximumInputColumns = numberColumns
    private val pointerToDeviceBackwardResults = Pointer.to(this.deviceBackwardResult)

    private val batchSize = intArrayOf(-1)
    private val pointerToBatchSize = Pointer.to(this.batchSize)

    override fun acquire(maximumBatchSize : Int) {

        this.trainingKernel = this.createTrainingKernel()
        this.runtimeKernel = this.createRuntimeKernel()

        val numberBatchEntries = maximumBatchSize * this.numberEntries

        val seeds = IntArray(numberBatchEntries)
        seed(this.random, seeds, numberBatchEntries)

        setIntArray(seeds, numberBatchEntries, this.deviceSeeds)

        allocateDeviceFloatMemory(this.deviceMasks, numberBatchEntries)
        allocateDeviceFloatMemory(this.deviceForwardResult, numberBatchEntries)

        this.backwardKernel = this.createBackwardKernel()
        allocateDeviceFloatMemory(this.deviceBackwardResult, numberBatchEntries)

        this.numberBlocksInXDimension = maximumBatchSize
        val launchConfiguration = computeEntrywiseLaunchConfiguration(this.numberEntries, this.numberMultiprocessors, this.numberResidentWarps, this.warpSize, this.maximumNumberThreadsPerBlock)
        this.numberBlocksInYDimension = launchConfiguration.numberBlocks
        this.numberThreadsPerBlock = launchConfiguration.numberThreadsPerBlock
        this.numberIterations[0] = launchConfiguration.numberIterations

    }

    override fun forward(batchSize: Int, deviceInput: Pointer, isTraining: Boolean): Pointer {

        this.batchSize[0] = batchSize

        val pointerToInput = Pointer.to(deviceInput)

        if(isTraining) {

            this.trainingKernel!!.launch(
                Pointer.to(
                    this.pointerToBatchSize,
                    this.pointerToNumberEntries,
                    this.pointerToNumberIterations,
                    this.pointerToDropoutProbability,
                    pointerToInput,
                    this.pointerToDeviceSeeds,
                    this.pointerToDeviceMasks,
                    this.pointerToDeviceForwardResults
                ),
                this.numberBlocksInXDimension,
                this.numberBlocksInYDimension,
                this.numberThreadsPerBlock,
                0
            )

        }
        else {

            this.runtimeKernel!!.launch(
                Pointer.to(
                    this.pointerToBatchSize,
                    this.pointerToNumberEntries,
                    this.pointerToNumberIterations,
                    this.pointerToKeepProbability,
                    pointerToInput,
                    this.pointerToDeviceForwardResults
                ),
                this.numberBlocksInXDimension,
                this.numberBlocksInYDimension,
                this.numberThreadsPerBlock,
                0
            )

        }

        return this.deviceForwardResult

    }


    override fun backward(batchSize: Int, chain: Pointer) : Pointer {

        this.backwardKernel!!.launch(
            Pointer.to(
                this.pointerToBatchSize,
                this.pointerToNumberEntries,
                this.pointerToNumberIterations,
                Pointer.to(chain),
                this.pointerToDeviceMasks,
                this.pointerToDeviceBackwardResults
            ),
            this.numberBlocksInXDimension,
            this.numberBlocksInYDimension,
            this.numberThreadsPerBlock,
            0
        )

        return this.deviceBackwardResult

    }

    override fun release() {

        this.trainingKernel!!.destroy()
        cudaFree(this.deviceBackwardResult)

        cudaFree(this.deviceForwardResult)
        cudaFree(this.deviceMasks)
        cudaFree(this.deviceSeeds)

        this.backwardKernel!!.destroy()
        this.runtimeKernel!!.destroy()

    }

}