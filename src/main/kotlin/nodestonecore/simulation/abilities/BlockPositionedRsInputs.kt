package com.sloimay.nodestonecore.simulation.abilities

import com.sloimay.nodestonecore.simulation.inputs.SimInput
import com.sloimay.nodestonecore.simulation.inputs.SimRsInput
import com.sloimay.smath.vectors.IVec3

/**
 * Whether the simulation has redstone inputs positioned by block positions.
 */
interface BlockPositionedRsInputs {

    /**
     * Returns an unmodifiable view of the block positioned redstone inputs
     * this simulation has. May change from tick to tick, but changes are reflected
     * through the map anyway, so you usually only have to call this function
     * once if you want a complete catalogue of redstone inputs.
     */
    fun getBlockPositionedRsInputs(): Map<IVec3, SimRsInput>

}