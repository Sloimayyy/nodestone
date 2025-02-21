package com.sloimay.threadstonecore.backends

import me.sloimay.mcvolume.IntBoundary
import me.sloimay.mcvolume.McVolume
import me.sloimay.mcvolume.block.BlockState
import me.sloimay.smath.vectors.IVec3

abstract class RedstoneSimBackend(val volume: McVolume, val simBounds: IntBoundary) {
    abstract fun tickWhile(pred: () -> Boolean)

    abstract fun updateRepr(
        updateVolume: Boolean = true,
        onlyNecessaryVisualUpdates: Boolean = true,
        renderCallback: (renderPos: IVec3, newBlockState: BlockState) -> Unit
    )

    abstract fun getInputNodePositions(): Set<IVec3>
    abstract fun scheduleButtonPress(ticksFromNow: Int, pressLength: Int, inputNodePos: IVec3)
    abstract fun scheduleUserInputChange(ticksFromNow: Int, inputNodePos: IVec3, power: Int)


}