package com.sloimay.threadstonecore.redstoneir.conns

import com.sloimay.mcvolume.McVolume
import com.sloimay.mcvolume.block.BlockState
import com.sloimay.smath.vectors.IVec3
import com.sloimay.threadstonecore.redstoneir.from.Direction
import com.sloimay.threadstonecore.redstoneir.from.plus
import com.sloimay.threadstonecore.redstoneir.helpers.BsHelper

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

        val origBs = v.getBlock(thisPos).state
        val forward = bsToForwardBasis(origBs)
        val forwardPos = thisPos + forward
        val forwardBs = v.getBlock(forwardPos).state

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
        val propOption = thisBs.getProp("facing")
        if (propOption.isEmpty) {
            throw Exception("No prop 'facing' in the inputted blockstate")
        }
        return Direction.fromProp(thisBs.getProp("facing").get()).opposite
    }

}