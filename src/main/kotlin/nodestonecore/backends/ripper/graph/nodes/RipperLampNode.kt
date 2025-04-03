package com.sloimay.nodestonecore.backends.ripper.graph.nodes

import com.sloimay.smath.vectors.IVec3
import com.sloimay.nodestonecore.backends.ripper.helpers.RipperHelper.Companion.toBitsInt
import com.sloimay.nodestonecore.backends.ripper.helpers.int

class RipperLampNode(
    pos: IVec3?,

    val startLit: Boolean,
) : RipperNode(pos) {
    override val ID: RipperNodeType = RipperNodeType.LAMP

    override fun getDataBits(): Int {
        return toBitsInt(
            startLit.int to 1,
        )
    }
}