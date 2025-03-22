package com.sloimay.threadstonecore.backends.mamba.graph.nodes

import com.sloimay.threadstonecore.backends.mamba.helpers.MambaHelper.Companion.toBitsInt
import com.sloimay.threadstonecore.backends.mamba.helpers.toInt
import me.sloimay.smath.vectors.IVec3

class MambaUserInputNode(
    pos: IVec3?,

    val startOutputSs: Int,
) : MambaNode(pos) {
    override val ID = MambaNodeType.USER_INPUT

    override fun getDataBits(): Int {
        return toBitsInt(
            startOutputSs to 4,
        )
    }
}