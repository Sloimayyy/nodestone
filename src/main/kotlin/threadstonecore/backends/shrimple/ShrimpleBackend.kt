package com.sloimay.threadstonecore.backends.shrimple

import com.sloimay.threadstonecore.backends.RedstoneSimBackend
import me.sloimay.mcvolume.IntBoundary
import me.sloimay.mcvolume.McVolume
import me.sloimay.mcvolume.block.BlockState
import me.sloimay.smath.vectors.IVec3

class ShrimpleBackend(volume: McVolume, simBounds: IntBoundary) : RedstoneSimBackend(volume, simBounds) {
    override fun tickWhile(pred: () -> Boolean) {
        TODO("Not yet implemented")
    }

    override fun updateRepr(
        updateVolume: Boolean,
        onlyNecessaryVisualUpdates: Boolean,
        renderCallback: (renderPos: IVec3, newBlockState: BlockState) -> Unit
    ) {
        TODO("Not yet implemented")
    }

    override fun getInputNodePositions(): Set<IVec3> {
        TODO("Not yet implemented")
    }

    override fun scheduleButtonPress(ticksFromNow: Int, pressLength: Int, inputNodePos: IVec3) {
        TODO("Not yet implemented")
    }

    override fun scheduleUserInputChange(ticksFromNow: Int, inputNodePos: IVec3, power: Int) {
        TODO("Not yet implemented")
    }
}