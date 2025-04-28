package com.sloimay.nodestonecore.simulation.backends.mamba.graph.nodes

import com.sloimay.nodestonecore.backends.mamba.helpers.MambaHelper.Companion.toBitsInt
import com.sloimay.nodestonecore.backends.mamba.helpers.toInt
import me.sloimay.smath.vectors.IVec3


class MambaComparatorNode(
    pos: IVec3?,

    val startOutputSs: Int,
    val farInputSs: Int, // -1 if none
    val mode: Boolean,
) : MambaNode(pos) {
    override val ID = MambaNodeType.COMPARATOR

    fun hasFarInput() = farInputSs != -1

    override fun getDataBits(): Int {
        return toBitsInt(
            startOutputSs to 4,
            this.hasFarInput().toInt() to 1,
            farInputSs to 4,
            mode.toInt() to 1,
        )
    }
}


