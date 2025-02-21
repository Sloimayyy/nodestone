package com.sloimay.threadstonecore.redstoneir.conns

import com.sloimay.mcvolume.McVolume
import com.sloimay.mcvolume.block.BlockState
import com.sloimay.smath.vectors.IVec3
import com.sloimay.threadstonecore.redstoneir.from.Direction
import com.sloimay.threadstonecore.redstoneir.from.plus
import com.sloimay.threadstonecore.redstoneir.helpers.BsHelper

class PressurePlateConns : NodeConns() {
    // Don't connect to buttons
    override val validIncomingConns: HashMap<IVec3, HashMap<RsConnType, OutputLinkType>> = hashMapOf()

    override fun bsToForwardBasis(thisBs: BlockState): Direction {
        return Direction.NORTH // Pressure plates don't have a direction
    }

    override fun outgoing(v: McVolume, thisPos: IVec3): List<RsConn> {
        val thisBs = v.getBlock(thisPos).state
        val outConns = mutableListOf<RsConn>()

        // Get whether to hard power
        val powerDir = Direction.DOWN
        val bsHardPoweredInto = v.getBlock(thisPos + powerDir).state
        val doHardPower = BsHelper.isConductive(bsHardPoweredInto)

        // Gen conns
        outConns.addAll(genHardPowerInDirDirectInAllOthers(v, thisPos, powerDir, doHardPower))
        outConns.add(RsConn(v, thisPos, powerDir.heading, RsConnType.DIRECT, thisPos + powerDir))

        return outConns
    }
}