package com.sloimay.nodestonecore.simulation.abilities

import com.sloimay.mcvolume.block.BlockState
import com.sloimay.smath.vectors.IVec3

/**
 * Whether you can request a simulation to send block state changes back
 *
 * TODO: find a better name
 */
interface BlockStateChangeRequestAbility {

    /**
     * Will call the callback lambda for every block state that changed since last time
     * we called this function. It is recommended for the position outputted to make sense
     * with the context in which that simulation is initialised. (If this backend is
     * volume initialised, then it would make the most sense if the positions inputted
     * in the callback were relative to the volume).
     * If the simulation doesn't rely on McVolumes, it should still output McVolume
     * BlockState objects, as they are context independent still, which is the most
     * important part of Nodestone.
     *
     * TODO: find a better name
     */
    fun requestBlockStateChanges(callback: (pos: IVec3, newBlockState: BlockState) -> Unit)

}