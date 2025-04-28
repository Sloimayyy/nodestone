package com.sloimay.nodestonecore.simulation.backends.shrimple.graph.nodes

import com.sloimay.smath.vectors.IVec3
import com.sloimay.nodestonecore.simulation.backends.shrimple.graph.nodes.ShrimpleNodeIntRepr.Companion.getIntReprFromBits
import com.sloimay.nodestonecore.simulation.backends.shrimple.helpers.ShrimpleHelper.Companion.getBitField
import com.sloimay.nodestonecore.simulation.backends.shrimple.helpers.ShrimpleHelper.Companion.toBitsInt

class ShrimpleConstantNode(
    pos: IVec3?,
    updatePriority: Int,

    val startSs: Int,
) : ShrimpleNode(pos, updatePriority) {
    override val type = ShrimpleNodeType.CONSTANT

    companion object {

        fun getDynDataOutputSs(dynData: Int): Int {
            return getBitField(dynData, 0, 4)
        }

        fun getPower(dynData: Int): Int {
            return getDynDataOutputSs(dynData)
        }

    }

    override fun getIntRepr(): Int {
        val dynamicDataBits = toBitsInt(startSs to 4)
        return getIntReprFromBits(
            false,
            type.int,
            updatePriority,
            0,
            dynamicDataBits,
            dynamicDataBits,
        )
    }
}