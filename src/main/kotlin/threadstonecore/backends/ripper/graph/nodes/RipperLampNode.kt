package com.sloimay.threadstonecore.backends.ripper.graph.nodes

import com.sloimay.threadstonecore.backends.ripper.helpers.RipperHelper.Companion.toBitsInt
import com.sloimay.threadstonecore.backends.ripper.helpers.int
import me.sloimay.smath.vectors.IVec3

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