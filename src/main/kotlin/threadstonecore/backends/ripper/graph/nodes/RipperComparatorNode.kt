package com.sloimay.threadstonecore.backends.ripper.graph.nodes



import com.sloimay.threadstonecore.backends.ripper.helpers.RipperHelper.Companion.toBitsInt
import com.sloimay.threadstonecore.backends.ripper.helpers.int
import com.sloimay.threadstonecore.helpers.toInt
import me.sloimay.smath.vectors.IVec3

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