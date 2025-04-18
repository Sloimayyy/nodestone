package com.sloimay.nodestonecore.redstoneir.conns

import com.sloimay.mcvolume.McVolume
import com.sloimay.mcvolume.block.BlockState
import com.sloimay.smath.vectors.IVec3
import com.sloimay.nodestonecore.redstoneir.from.Direction
import com.sloimay.nodestonecore.redstoneir.from.plus
import com.sloimay.nodestonecore.redstoneir.helpers.BsHelper

class RepeaterConns : NodeConns() {

    // Incoming from <relative dir>
    override val validIncomingConns = hashMapOf(
        RelDirs.RIGHT to hashMapOf(
            RsConnType.DIRECT_COMPREP to OutputLinkType.SIDE,
        ),
        RelDirs.LEFT to hashMapOf(
            RsConnType.DIRECT_COMPREP to OutputLinkType.SIDE,
        ),
        RelDirs.BACKWARD to hashMapOf(
            RsConnType.ANY to OutputLinkType.NORMAL,
        )
    )

    override fun outgoing(v: McVolume, thisPos: IVec3): List<RsConn> {
        val outConns = mutableListOf<RsConn>()

        val origBs = v.getBlockState(thisPos)
        val forward = bsToForwardBasis(origBs)
        val forwardPos = thisPos + forward
        val forwardBs = v.getBlockState(forwardPos)

        // Repeater powering directly in front of it
        outConns.add(RsConn(v, thisPos, forward.heading, RsConnType.DIRECT_COMPREP, thisPos + forward))
        // Repeater hard powering in front of it
        if (BsHelper.isConductive(forwardBs)) {
            outConns.addAll(genBlockPowerConns(v, forwardPos, hardPower = true))
        }

        return outConns.toList()
    }


    override fun bsToForwardBasis(thisBs: BlockState): Direction {
        if (thisBs.fullName != "minecraft:repeater") {
            throw Exception("Inputted blockstate isn't of the correct state for the repeater node")
        }
        val prop = thisBs.getProp("facing") ?: error("No prop 'facing' in the inputted blockstate")
        return Direction.fromProp(prop).opposite
    }

}