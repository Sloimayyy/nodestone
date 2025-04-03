package com.sloimay.nodestonecore.backends

import com.sloimay.mcvolume.IntBoundary
import com.sloimay.mcvolume.McVolume
import com.sloimay.mcvolume.block.BlockState
import com.sloimay.smath.vectors.IVec3


abstract class RedstoneSimBackend(val volume: McVolume, val simBounds: IntBoundary) {
    abstract fun tickWhile(pred: () -> Boolean)

    abstract fun updateRepr(
        updateVolume: Boolean = true,
        onlyNecessaryVisualUpdates: Boolean = true,
        renderCallback: (renderPos: IVec3, newBlockState: BlockState) -> Unit
    )

    abstract fun getInputs(): List<RedstoneSimInput>

    abstract fun scheduleButtonPress(ticksFromNow: Int, pressLength: Int, input: RedstoneSimInput)
    abstract fun scheduleUserInputChange(ticksFromNow: Int, input: RedstoneSimInput, power: Int)
}