package com.sloimay.nodestonecore.simulation.backends.shrimple.graph.nodes

import com.sloimay.smath.vectors.IVec3
import com.sloimay.nodestonecore.simulation.backends.shrimple.graph.nodes.ShrimpleNodeIntRepr.Companion.getIntReprFromBits
import com.sloimay.nodestonecore.simulation.backends.shrimple.helpers.ShrimpleHelper.Companion.getBitField
import com.sloimay.nodestonecore.simulation.backends.shrimple.helpers.ShrimpleHelper.Companion.setBitField
import com.sloimay.nodestonecore.simulation.backends.shrimple.helpers.ShrimpleHelper.Companion.toBitsInt
import com.sloimay.nodestonecore.simulation.backends.shrimple.helpers.int

class ShrimpleLampNode(
    pos: IVec3?,
    updatePriority: Int,

    val startLit: Boolean,
) : ShrimpleNode(pos, updatePriority) {
    override val type = ShrimpleNodeType.LAMP

    companion object {
        fun getDynDataLit(nodeDynData: Int): Int {
            return getBitField(nodeDynData, 0, 1)
        }

        fun setDynDataLit(nodeDynData: Int, litBit: Int): Int {
            return setBitField(nodeDynData, litBit, 0, 1)
        }
    }

    override fun getIntRepr(): Int {
        val dynamicDataBits = toBitsInt(startLit.int to 1)
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