package shape.komputation.cuda.layers.forward.activation

import shape.komputation.cuda.Kernel

class CudaTanhLayer internal constructor(
    name : String? = null,
    numberEntries : Int,
    createForwardKernel: () -> Kernel,
    createBackwardKernel: () -> Kernel,
    numberMultiprocessors : Int,
    numberResidentWarps : Int,
    warpSize : Int,
    maximumNumberThreadsPerBlock : Int) : BaseCudaEntrywiseActivationLayer(name, createForwardKernel, createBackwardKernel, numberEntries, numberMultiprocessors, numberResidentWarps, warpSize, maximumNumberThreadsPerBlock)