package com.sloimay.threadstonecore.backends.shrimple.graph.nodes

import com.sloimay.threadstonecore.backends.shrimple.graph.nodes.ShrimpleNodeIntRepr.Companion.getIntReprFromBits
import com.sloimay.threadstonecore.backends.shrimple.helpers.ShrimpleHelper.Companion.toBitsInt
import com.sloimay.threadstonecore.backends.shrimple.helpers.int
import me.sloimay.smath.vectors.IVec3

class ShrimpleRepeaterNode(
    pos: IVec3?,

    val startSchedulerBits: Int,
    val startLocked: Boolean,

    val delay: Int,
) : ShrimpleNode(pos) {
    override val type = ShrimpleNodeType.REPEATER

    override fun getIntRepr(): Int {
        val dynamicDataBits = toBitsInt(startSchedulerBits to 4, startLocked.int to 1)
        return getIntReprFromBits(
            false,
            type.int,
            toBitsInt((delay-1) to 2),
            dynamicDataBits,
            dynamicDataBits,
        )
    }
}