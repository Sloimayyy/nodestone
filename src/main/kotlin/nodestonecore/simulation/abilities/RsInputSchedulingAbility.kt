package com.sloimay.nodestonecore.simulation.abilities

import com.sloimay.nodestonecore.simulation.inputs.SimRsInput


/**
 * Whether this simulation has the ability to schedule redstone inputs.
 */
interface RsInputSchedulingAbility {


    /**
     * Schedules a redstone input to be of the inputted power level, for
     * the amount of time of {tickLength} and in {ticksFromNow} ticks.
     */
    fun scheduleRsInput(input: SimRsInput, ticksFromNow: Int, tickLength: Int, power: Int)


}