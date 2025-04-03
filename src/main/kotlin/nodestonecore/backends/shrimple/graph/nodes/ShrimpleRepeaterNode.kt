package com.sloimay.nodestonecore.backends.shrimple.graph.nodes

import com.sloimay.smath.vectors.IVec3
import com.sloimay.nodestonecore.backends.shrimple.graph.nodes.ShrimpleNodeIntRepr.Companion.getIntReprFromBits
import com.sloimay.nodestonecore.backends.shrimple.helpers.ShrimpleHelper.Companion.getBitField
import com.sloimay.nodestonecore.backends.shrimple.helpers.ShrimpleHelper.Companion.setBitField
import com.sloimay.nodestonecore.backends.shrimple.helpers.ShrimpleHelper.Companion.toBitsInt
import com.sloimay.nodestonecore.backends.shrimple.helpers.int

class ShrimpleRepeaterNode(
    pos: IVec3?,
    updatePriority: Int,

    val startSchedulerBits: Int,
    val startLocked: Boolean,
    val startUpdateTimer: Int,

    val delay: Int,
) : ShrimpleNode(pos, updatePriority) {
    override val type = ShrimpleNodeType.REPEATER

    companion object {

        fun getDynDataScheduler(dynData: Int): Int {
            return getBitField(dynData, 0, 4)
        }

        fun setDynDataScheduler(nodeDynData: Int, schedulerBits: Int): Int {
            return setBitField(nodeDynData, schedulerBits, 0, 4)
        }

        fun getDynDataLocked(dynData: Int): Int {
            return getBitField(dynData, 4, 1)
        }

        fun setDynDataLocked(nodeDynData: Int, lockedBit: Int): Int {
            return setBitField(nodeDynData, lockedBit, 4, 1)
        }

        fun getDynDataUpdateTimer(dynData: Int): Int {
            return getBitField(dynData, 5, 3)
        }

        fun setDynDataUpdateTimer(nodeDynData: Int, updateTimer: Int): Int {
            return setBitField(nodeDynData, updateTimer, 5, 3)
        }

        fun getConstDataDelay(constData: Int): Int {
            return getBitField(constData, 0, 2)
        }


        fun getPower(nodeDynData: Int): Int {
            val scheduler = getDynDataScheduler(nodeDynData)
            val isPowered = scheduler and 0x1
            return isPowered * 15
        }

    }

    override fun getIntRepr(): Int {
        val dynamicDataBits = toBitsInt(startSchedulerBits to 4, startLocked.int to 1, startUpdateTimer to 3)
        return getIntReprFromBits(
            false,
            type.int,
            updatePriority,
            toBitsInt((delay-1) to 2),
            dynamicDataBits,
            dynamicDataBits,
        )
    }
}