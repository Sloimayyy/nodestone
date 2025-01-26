package com.sloimay.threadstonecore.backends

import me.sloimay.mcvolume.IntBoundary
import me.sloimay.mcvolume.McVolume
import com.sloimay.threadstonecore.backends.gpubackend.gpursgraph.nodes.UserInputNodeGpu
import me.sloimay.mcvolume.block.BlockState
import me.sloimay.smath.vectors.IVec3

abstract class RedstoneSimBackend(val volume: McVolume, val simBounds: IntBoundary) {
    abstract fun tickWhile(pred: () -> Boolean)

    abstract fun updateRepr(
        updateVolume: Boolean = true,
        onlyNecessary: Boolean = true,
        renderCallback: (renderPos: IVec3, newBlockState: BlockState) -> Unit
    )

    abstract fun getInputNodeAt(nodePos: IVec3): UserInputNodeGpu?
    abstract fun scheduleButtonPress(ticksFromNow: Int, pressLength: Int, inputNode: UserInputNodeGpu)
    abstract fun scheduleUserInputChange(ticksFromNow: Int, inputNode: UserInputNodeGpu, power: Int)

}