package com.sloimay.nodestonecore.redstoneir.conns

import com.sloimay.mcvolume.McVolume
import com.sloimay.mcvolume.block.BlockState
import com.sloimay.smath.vectors.IVec3
import com.sloimay.smath.vectors.ivec3
import com.sloimay.nodestonecore.redstoneir.from.Direction
import com.sloimay.nodestonecore.redstoneir.from.plus
import com.sloimay.nodestonecore.redstoneir.helpers.BsHelper

class TorchConns : NodeConns() {

    // Incoming from <relative dir>
    // North is the front of the torch. A torch doesnt have orientation so we have to choose one
    override val validIncomingConns = hashMapOf(
        RelDirs.DOWN to hashMapOf(
            RsConnType.SOFT_POWER to OutputLinkType.NORMAL,
            RsConnType.HARD_POWER to OutputLinkType.NORMAL,
        ),
    )

    override fun outgoing(v: McVolume, thisPos: IVec3): List<RsConn> {
        val outConns = mutableListOf<RsConn>()

        val origBs = v.getBlock(thisPos).state
        val forward = bsToForwardBasis(origBs)
        val forwardPos = thisPos + forward
        val forwardBs = v.getBlock(forwardPos).state

        val abovePos = thisPos + ivec3(0, 1, 0)
        val aboveBs = v.getBlock(abovePos).state

        // Torch powering directly above
        outConns.add(RsConn(v, thisPos, Direction.UP.heading, RsConnType.DIRECT, thisPos + Direction.UP))
        // Torch hard powering above
        if (BsHelper.isConductive(aboveBs)) {
            outConns.addAll(genBlockPowerConns(v, abovePos, hardPower = true))
        }
        // Torch powering directly to the sides
        for (d in Direction.cardinal) {
            outConns.add(RsConn(v, thisPos, d.heading, RsConnType.DIRECT, thisPos + d))
        }


        return outConns.toList()
    }

    override fun bsToForwardBasis(thisBs: BlockState): Direction {
        if (thisBs.fullName != "minecraft:redstone_torch") {
            throw Exception("Inputted blockstate isn't of the correct state for the redstone torch node")
        }
        return Direction.NORTH
    }

}