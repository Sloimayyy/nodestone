package com.sloimay.nodestonecore.simulation.backends.ripper.graph.nodes



import com.sloimay.smath.vectors.IVec3
import com.sloimay.nodestonecore.backends.ripper.helpers.RipperHelper.Companion.toBitsInt
import com.sloimay.nodestonecore.helpers.toInt

class RipperComparatorNode(
    pos: IVec3?,

    val startOutputSs: Int,
    val farInputSs: Int, // -1 if none
    val mode: Boolean,
) : RipperNode(pos) {
    override val ID: RipperNodeType = RipperNodeType.COMPARATOR

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