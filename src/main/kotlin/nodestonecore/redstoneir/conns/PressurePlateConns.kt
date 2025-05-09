package com.sloimay.nodestonecore.redstoneir.conns

import com.sloimay.mcvolume.McVolume
import com.sloimay.mcvolume.block.BlockState
import com.sloimay.smath.vectors.IVec3
import com.sloimay.nodestonecore.redstoneir.from.Direction
import com.sloimay.nodestonecore.redstoneir.from.plus
import com.sloimay.nodestonecore.redstoneir.helpers.BsHelper

class PressurePlateConns : NodeConns() {
    // Don't connect to buttons
    override val validIncomingConns: HashMap<IVec3, HashMap<RsConnType, OutputLinkType>> = hashMapOf()

    override fun bsToForwardBasis(thisBs: BlockState): Direction {
        return Direction.NORTH // Pressure plates don't have a direction
    }

    override fun outgoing(v: McVolume, thisPos: IVec3): List<RsConn> {
        val thisBs = v.getBlockState(thisPos)
        val outConns = mutableListOf<RsConn>()

        // Get whether to hard power
        val powerDir = Direction.DOWN
        val bsHardPoweredInto = v.getBlockState(thisPos + powerDir)
        val doHardPower = BsHelper.isConductive(bsHardPoweredInto)

        // Gen conns
        outConns.addAll(genHardPowerInDirDirectInAllOthers(v, thisPos, powerDir, doHardPower))
        outConns.add(RsConn(v, thisPos, powerDir.heading, RsConnType.DIRECT, thisPos + powerDir))

        return outConns
    }
}