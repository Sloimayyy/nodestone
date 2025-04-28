package com.sloimay.nodestonecore.simulation


abstract class SimBackend {

    /*
    abstract fun tickWhile(pred: () -> Boolean)

    abstract fun updateRepr(
        updateVolume: Boolean = true,
        onlyNecessaryVisualUpdates: Boolean = true,
        renderCallback: (renderPos: IVec3, newBlockState: BlockState) -> Unit
    )

    abstract fun getInputs(): List<RedstoneSimInput>

    abstract fun scheduleButtonPress(ticksFromNow: Int, pressLength: Int, input: RedstoneSimInput)
    abstract fun scheduleUserInputChange(ticksFromNow: Int, input: RedstoneSimInput, power: Int)
     */


    /**
     * Returns true if the simulation is fully initialised. (Generally
     * means it's ready to be ticked).
     */
    abstract fun isReady(): Boolean

}