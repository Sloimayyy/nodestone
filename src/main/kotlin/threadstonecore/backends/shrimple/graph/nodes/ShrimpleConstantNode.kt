package com.sloimay.threadstonecore.backends.shrimple.graph.nodes

import com.sloimay.threadstonecore.backends.shrimple.graph.nodes.ShrimpleNodeIntRepr.Companion.getIntReprFromBits
import com.sloimay.threadstonecore.backends.shrimple.helpers.ShrimpleHelper.Companion.toBitsInt
import com.sloimay.threadstonecore.backends.shrimple.helpers.int
import me.sloimay.smath.vectors.IVec3

class ShrimpleConstantNode(
    pos: IVec3?,

    val startSs: Int,
) : ShrimpleNode(pos) {
    override val type = ShrimpleNodeType.CONSTANT

    override fun getIntRepr(): Int {
        val dynamicDataBits = toBitsInt(startSs to 4)
        return getIntReprFromBits(
            false,
            type.int,
            0,
            dynamicDataBits,
            dynamicDataBits,
        )
    }
}