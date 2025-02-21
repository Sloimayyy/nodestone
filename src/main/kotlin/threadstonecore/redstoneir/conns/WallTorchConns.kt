package com.sloimay.threadstonecore.redstoneir.conns

import com.sloimay.mcvolume.McVolume
import com.sloimay.mcvolume.block.BlockState
import com.sloimay.smath.vectors.IVec3
import com.sloimay.smath.vectors.ivec3
import com.sloimay.threadstonecore.redstoneir.from.Direction
import com.sloimay.threadstonecore.redstoneir.from.plus
import com.sloimay.threadstonecore.redstoneir.helpers.BsHelper

class WallTorchConns : NodeConns() {

    // Incoming from <relative dir>
    // North is the front of the torch. A torch doesnt have orientation so we have to choose one
    override val validIncomingConns = hashMapOf(
        RelDirs.BACKWARD to hashMapOf(
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
        // Torch powering directly to the sides except its resting side
        for (d in Direction.cardinal) {
            if (d == forward.opposite) continue
            outConns.add(RsConn(v, thisPos, d.heading, RsConnType.DIRECT, thisPos + d))
        }
        // Torch powering directly below
        outConns.add(RsConn(v, thisPos, RelDirs.DOWN, RsConnType.DIRECT, thisPos + Direction.DOWN))


        return outConns.toList()
    }

    override fun bsToForwardBasis(thisBs: BlockState): Direction {
        if (thisBs.fullName != "minecraft:redstone_wall_torch") {
            throw Exception("Inputted blockstate isn't of the correct state for the redstone wall torch node")
        }
        val propOption = thisBs.getProp("facing")
        if (propOption.isEmpty) {
            throw Exception("No prop 'facing' in the inputted blockstate")
        }
        return Direction.fromProp(thisBs.getProp("facing").get())
    }

}