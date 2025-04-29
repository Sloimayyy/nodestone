package com.sloimay.nodestonecore.simulation.initinterfaces

import com.sloimay.mcvolume.McVolume
import com.sloimay.smath.geometry.boundary.IntBoundary


/**
 * Whether this simulation's starting state is represented by a
 * world area.
 */
interface AreaRepresentationInitialized {

    fun withAreaRepresentation(mcVolume: McVolume, areaBounds: IntBoundary)

}