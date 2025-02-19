package com.sloimay.threadstonecore.backends.shrimple.graph.nodes

import com.sloimay.threadstonecore.backends.shrimple.helpers.ShrimpleHelper.Companion.toBitsInt
import com.sloimay.threadstonecore.backends.shrimple.helpers.int
import me.sloimay.smath.vectors.IVec3

class ShrimpleComparatorNode(
    pos: IVec3?,

    val startOutputSs: Int,

    val hasFarInputSs: Boolean,
    val farInputSs: Int,
    val mode: Boolean,
) : ShrimpleNode(pos) {
    override val type = ShrimpleNodeType.COMPARATOR

    override fun getIntRepr(): Int {
        val dynamicDataBits = toBitsInt(startOutputSs to 4)
        return getIntReprFromBits(
            false,
            type.int,
            toBitsInt(hasFarInputSs.int to 1, farInputSs to 4, mode.int to 1),
            dynamicDataBits,
            dynamicDataBits,
        )
    }
}