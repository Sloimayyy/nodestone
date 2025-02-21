package com.sloimay.threadstonecore.backends.shrimple.graph.nodes

import com.sloimay.threadstonecore.backends.shrimple.graph.nodes.ShrimpleNodeIntRepr.Companion.getIntReprFromBits
import com.sloimay.threadstonecore.backends.shrimple.helpers.ShrimpleHelper.Companion.getBitField
import com.sloimay.threadstonecore.backends.shrimple.helpers.ShrimpleHelper.Companion.setBitField
import com.sloimay.threadstonecore.backends.shrimple.helpers.ShrimpleHelper.Companion.toBitsInt
import com.sloimay.threadstonecore.backends.shrimple.helpers.int
import me.sloimay.smath.vectors.IVec3

class ShrimpleComparatorNode(
    pos: IVec3?,
    updatePriority: Int,

    val startOutputSs: Int,

    val hasFarInputSs: Boolean,
    val farInputSs: Int,
    val mode: Boolean,
) : ShrimpleNode(pos, updatePriority) {
    override val type = ShrimpleNodeType.COMPARATOR

    companion object {

        fun getDynDataOutputSs(dynData: Int): Int {
            return getBitField(dynData, 0, 4)
        }

        fun setDynDataOutputSs(dynData: Int, outputSs: Int): Int {
            return setBitField(dynData, outputSs, 0, 4)
        }

        fun getConstDataHasFarInput(constData: Int): Int {
            return getBitField(constData, 0, 1)
        }

        fun getConstDataFarInputSs(constData: Int): Int {
            return getBitField(constData, 1, 4)
        }

        fun getConstDataMode(constData: Int): Int {
            return getBitField(constData, 5, 1)
        }


        fun getPower(dynData: Int): Int {
            return getDynDataOutputSs(dynData)
        }

    }

    override fun getIntRepr(): Int {
        val dynamicDataBits = toBitsInt(startOutputSs to 4)
        return getIntReprFromBits(
            false,
            type.int,
            updatePriority,
            toBitsInt(hasFarInputSs.int to 1, farInputSs to 4, mode.int to 1),
            dynamicDataBits,
            dynamicDataBits,
        )
    }
}