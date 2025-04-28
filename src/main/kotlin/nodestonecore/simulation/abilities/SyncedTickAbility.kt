package com.sloimay.nodestonecore.simulation.abilities

/**
 * Whether the simulation can be ticked, and all its computations
 * happen in the same thread as the caller.
 */
interface SyncedTickAbility {

    fun syncedTickWhile(pred: () -> Boolean)

}