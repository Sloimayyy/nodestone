package com.sloimay.threadstonecore.backends.ripper.graph.nodes

import com.sloimay.smath.vectors.IVec3
import com.sloimay.threadstonecore.backends.ripper.helpers.RipperHelper.Companion.toBitsInt
import com.sloimay.threadstonecore.backends.ripper.helpers.int

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