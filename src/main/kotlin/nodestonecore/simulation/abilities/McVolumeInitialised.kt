package com.sloimay.nodestonecore.simulation.abilities

import com.sloimay.mcvolume.McVolume


/**
 * Whether this simulation can be initialised using an McVolume.
 */
interface McVolumeInitialised {

    fun init(vol: McVolume): Boolean

}