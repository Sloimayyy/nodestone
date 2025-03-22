package com.sloimay.threadstonecore.backends.ripper.graph.nodes

import com.sloimay.smath.vectors.IVec3
import com.sloimay.threadstonecore.backends.ripper.helpers.RipperHelper.Companion.toBitsInt
import com.sloimay.threadstonecore.backends.ripper.helpers.int

class RipperTorchNode(
    pos: IVec3?,

    val startLit: Boolean,
) : RipperNode(pos) {
    override val ID: RipperNodeType = RipperNodeType.TORCH

    override fun getDataBits(): Int {
        return toBitsInt(
            startLit.int to 1,
        )
    }
}