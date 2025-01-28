package com.sloimay.threadstonecore.backends.mamba

import com.sloimay.threadstonecore.backends.RedstoneSimBackend
import com.sloimay.threadstonecore.redstoneir.RedstoneBuildIR
import com.sloimay.threadstonecore.redstoneir.from.fromVolume
import me.sloimay.mcvolume.IntBoundary
import me.sloimay.mcvolume.McVolume
import me.sloimay.mcvolume.block.BlockState
import me.sloimay.smath.vectors.IVec3


private const val WORK_GROUP_SIZE = 256



/**
 *
 * New layout for a gpu backend
 *
 */
class MambaBackend private constructor(
    volume: McVolume,
    simBounds: IntBoundary,



) : RedstoneSimBackend(volume, simBounds) {

    var ticksElapsed = 0



    companion object {

        fun new(vol: McVolume, simVolBounds: IntBoundary): MambaBackend {
            val irGraph = RedstoneBuildIR.fromVolume(vol)


            TODO()
        }

    }

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