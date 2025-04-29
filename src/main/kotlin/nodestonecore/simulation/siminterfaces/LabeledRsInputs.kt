package com.sloimay.nodestonecore.simulation.abilities

import com.sloimay.nodestonecore.simulation.inputs.SimRsInput

/**
 * Whether the simulation has labeled redstone inputs.
 */
interface LabeledRsInputs {

    /**
     * Returns an unmodifiable view of the labeled redstone inputs
     * this simulation has. May change from tick to tick, but changes are reflected
     * through the map anyway, so you usually only have to call this function
     * once if you want a complete catalogue of redstone inputs.
     */
    fun getBlockPositionedRsInputs(): Map<String, SimRsInput>

}