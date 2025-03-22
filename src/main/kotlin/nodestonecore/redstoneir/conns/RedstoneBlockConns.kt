package com.sloimay.threadstonecore.redstoneir.conns

import com.sloimay.mcvolume.McVolume
import com.sloimay.mcvolume.block.BlockState
import com.sloimay.smath.vectors.IVec3
import com.sloimay.threadstonecore.redstoneir.from.Direction
import com.sloimay.threadstonecore.redstoneir.from.plus

class RedstoneBlockConns : NodeConns() {
    // Don't connect to redstone blocks
    override val validIncomingConns: HashMap<IVec3, HashMap<RsConnType, OutputLinkType>> = hashMapOf()

    override fun bsToForwardBasis(thisBs: BlockState): Direction {
        return Direction.NORTH // Redstone blocks don't have a direction
    }

    override fun outgoing(v: McVolume, thisPos: IVec3): List<RsConn> {
        val outConns = mutableListOf<RsConn>()

        // Gen direct in every direction
        for (d in Direction.allDirs) {
            outConns.add(RsConn(v, thisPos, d.heading, RsConnType.DIRECT, thisPos + d))
        }

        return outConns
    }
}