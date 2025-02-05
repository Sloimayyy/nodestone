package com.sloimay.threadstonecore.backends.ripper.graph.nodes

import com.sloimay.threadstonecore.backends.ripper.helpers.RipperHelper.Companion.toBitsInt
import com.sloimay.threadstonecore.backends.ripper.helpers.int
import me.sloimay.smath.vectors.IVec3

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