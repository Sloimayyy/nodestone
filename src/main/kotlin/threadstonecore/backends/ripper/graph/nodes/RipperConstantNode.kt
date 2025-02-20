package com.sloimay.threadstonecore.backends.ripper.graph.nodes

import com.sloimay.threadstonecore.backends.ripper.helpers.RipperHelper.Companion.toBitsInt
import com.sloimay.threadstonecore.backends.ripper.helpers.int
import me.sloimay.smath.vectors.IVec3

class RipperConstantNode(
    pos: IVec3?,

    val ss: Int,
) : RipperNode(pos) {
    override val ID: RipperNodeType = RipperNodeType.CONSTANT

    override fun getDataBits(): Int {
        return toBitsInt(
            ss to 4,
        )
    }
}