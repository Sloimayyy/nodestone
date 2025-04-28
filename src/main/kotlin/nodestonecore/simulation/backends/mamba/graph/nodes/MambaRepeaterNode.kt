package com.sloimay.nodestonecore.simulation.backends.mamba.graph.nodes

import com.sloimay.nodestonecore.backends.mamba.helpers.MambaHelper.Companion.toBitsInt
import com.sloimay.nodestonecore.backends.mamba.helpers.toInt
import me.sloimay.smath.vectors.IVec3


class MambaRepeaterNode(
    pos: IVec3?,

    val startPowered: Boolean,
    val startLocked: Boolean,
    val realDelay: Int,
) : MambaNode(pos) {
    override val ID = MambaNodeType.REPEATER

    override fun getDataBits(): Int {
        val schedulerMask = (1 shl (realDelay)) - 1
        val schedulerBits = -startPowered.toInt() and schedulerMask
        return toBitsInt(
            schedulerBits to 4,
            startLocked.toInt() to 1,
            (realDelay - 1) to 2,
        )
    }
}


