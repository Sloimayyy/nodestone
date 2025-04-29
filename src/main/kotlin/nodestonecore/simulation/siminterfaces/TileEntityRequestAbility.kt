package com.sloimay.nodestonecore.simulation.siminterfaces

import com.sloimay.smath.vectors.IVec3
import net.querz.nbt.tag.CompoundTag

/**
 * Ability to request all tile entity data from the simulation.
 */
interface TileEntityRequestAbility {

    /**
     * TODO: make it an iterator
     */
    fun requestTileEntities(): List<Pair<IVec3, CompoundTag>>

}