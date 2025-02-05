package com.sloimay.threadstonecore.backends.ripper.graph.nodes

import com.sloimay.threadstonecore.backends.ripper.helpers.RipperHelper.Companion.toBitsInt
import me.sloimay.smath.vectors.IVec3

class RipperUserInputNode(
    pos: IVec3?,

    val startOutputSs: Int,
) : RipperNode(pos) {
    override val ID: RipperNodeType = RipperNodeType.USER_INPUT

    override fun getDataBits(): Int {
        return toBitsInt(
            startOutputSs to 4,
        )
    }
}