package com.sloimay.threadstonecore.redstoneir.conns

import com.sloimay.mcvolume.McVolume
import com.sloimay.mcvolume.block.BlockState
import com.sloimay.smath.vectors.IVec3
import com.sloimay.threadstonecore.redstoneir.from.Direction

class LampConns : NodeConns() {
    // Don't connect to redstone blocks
    override val validIncomingConns: HashMap<IVec3, HashMap<RsConnType, OutputLinkType>> = hashMapOf(
        RelDirs.RIGHT to hashMapOf(RsConnType.ANY to OutputLinkType.NORMAL),
        RelDirs.LEFT to hashMapOf(RsConnType.ANY to OutputLinkType.NORMAL),
        RelDirs.UP to hashMapOf(RsConnType.ANY to OutputLinkType.NORMAL),
        RelDirs.DOWN to hashMapOf(RsConnType.ANY to OutputLinkType.NORMAL),
        RelDirs.FORWARD to hashMapOf(RsConnType.ANY to OutputLinkType.NORMAL),
        RelDirs.BACKWARD to hashMapOf(RsConnType.ANY to OutputLinkType.NORMAL),
    )

    override fun bsToForwardBasis(thisBs: BlockState): Direction {
        return Direction.NORTH // Redstone lamps don't have a direction
    }

    override fun outgoing(v: McVolume, thisPos: IVec3): List<RsConn> {
        val outConns = mutableListOf<RsConn>()

        // Lamps don't go anywhere

        return outConns
    }
}