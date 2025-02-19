package com.sloimay.threadstonecore.backends.shrimple.graph.nodes

import com.sloimay.threadstonecore.backends.shrimple.helpers.ShrimpleHelper.Companion.toBitsInt
import com.sloimay.threadstonecore.backends.shrimple.helpers.int
import me.sloimay.smath.vectors.IVec3

class ShrimpleLampNode(
    pos: IVec3?,

    val startLit: Boolean,
) : ShrimpleNode(pos) {
    override val type = ShrimpleNodeType.LAMP

    override fun getIntRepr(): Int {
        val dynamicDataBits = toBitsInt(startLit.int to 1)
        return getIntReprFromBits(
            false,
            type.int,
            0,
            dynamicDataBits,
            dynamicDataBits,
        )
    }
}