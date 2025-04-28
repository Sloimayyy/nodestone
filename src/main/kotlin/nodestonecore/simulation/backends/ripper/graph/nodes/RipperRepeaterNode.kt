package com.sloimay.nodestonecore.simulation.backends.ripper.graph.nodes

import com.sloimay.smath.vectors.IVec3
import com.sloimay.nodestonecore.backends.ripper.helpers.RipperHelper.Companion.toBitsInt
import com.sloimay.nodestonecore.backends.ripper.helpers.int

class RipperRepeaterNode(
    pos: IVec3?,

    val startPowered: Boolean,
    val startLocked: Boolean,
    val realDelay: Int,
) : RipperNode(pos) {
    override val ID = RipperNodeType.REPEATER

    override fun getDataBits(): Int {
        val schedulerMask = (1 shl (realDelay)) - 1
        val schedulerBits = -startPowered.int and schedulerMask
        return toBitsInt(
            schedulerBits to 4,
            startLocked.int to 1,
            (realDelay - 1) to 2,
        )
    }
}