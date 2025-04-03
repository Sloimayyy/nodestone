package com.sloimay.nodestonecore.backends.mamba.graph.nodes

import com.sloimay.nodestonecore.backends.mamba.helpers.MambaHelper.Companion.toBitsInt
import com.sloimay.nodestonecore.backends.mamba.helpers.toInt
import me.sloimay.smath.vectors.IVec3


class MambaTorchNode(
    pos: IVec3?,

    val startLit: Boolean,
) : MambaNode(pos) {
    override val ID = MambaNodeType.TORCH

    override fun getDataBits(): Int {
        return toBitsInt(
            startLit.toInt() to 1,
        )
    }
}


