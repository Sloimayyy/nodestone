package com.sloimay.threadstonecore.backends.gpubackend.gpursgraph

import me.sloimay.mcvolume.McVolume
import me.sloimay.mcvolume.block.BlockState
import com.sloimay.threadstonecore.helpers.BsHelper
import me.sloimay.smath.vectors.IVec3
import me.sloimay.smath.vectors.ivec3
import me.sloimay.smath.vectors.swizzles.xxy
import me.sloimay.smath.vectors.swizzles.xz

typealias BlockStateToDirFunc = (BlockState) -> Direction

enum class RsConnType {
    ANY, // Just some nicety

    SOFT_POWER, // Subset of hardpower
    HARD_POWER,

    DIRECT, // Direct not compred
    DIRECT_COMPREP, // Direct comparator / repeater
}


class RelDirs {
    companion object {
        val FORWARD = ivec3(0, 0, 1)
        val BACKWARD = ivec3(0, 0, -1)
        val RIGHT = ivec3(-1, 0, 0)
        val LEFT = ivec3(1, 0, 0)
        val UP = ivec3(0, 1, 0)
        val DOWN = ivec3(0, -1, 0)

        val HORIZ = listOf(FORWARD, RIGHT, BACKWARD, LEFT)
    }
}


enum class OutputLinkType {
    NORMAL,
    SIDE,
    NONE,
}

val BS_TO_CONNS = listOf(
    BlockState.fromStr("minecraft:redstone_wire") to RedstoneWireConns(),
    BlockState.fromStr("minecraft:redstone_torch") to TorchConns(),
    BlockState.fromStr("minecraft:redstone_wall_torch") to WallTorchConns(),
    BlockState.fromStr("minecraft:repeater") to RepeaterConns(),
    BlockState.fromStr("minecraft:comparator") to ComparatorConns(),
    BlockState.fromStr("minecraft:lever") to LeverConns(),
    BlockState.fromStr("minecraft:stone_button") to ButtonConns(),
    BlockState.fromStr("minecraft:stone_pressure_plate") to PressurePlateConns(),
    BlockState.fromStr("minecraft:redstone_block") to RedstoneBlockConns(),
    BlockState.fromStr("minecraft:redstone_lamp") to RedstoneLampConns(),

)

data class RsConn(val v: McVolume,
                  val connOrigin: IVec3,
                  val connDir: IVec3,
                  val connType: RsConnType,
                  val blockConnectedIntoPos: IVec3)


private val COMPARATOR_EXCEPTIONS = listOf(
    "minecraft:redstone_torch",
    "minecraft:redstone_wall_torch",
    "minecraft:lever",
    "minecraft:stone_button",
    "minecraft:stone_pressure_plate",
)


abstract class NodeConns {

    abstract val validIncomingConns: HashMap<IVec3, HashMap<RsConnType, OutputLinkType>>

    //abstract val outgoingConns: HashMap<IVec3, Pair<RsConnType, OutgoingConnValidator>>

    abstract fun bsToForwardBasis(thisBs: BlockState): Direction

    abstract fun outgoing(v: McVolume, thisPos: IVec3): List<RsConn>

    /**
     * @param incConnOffset: Offset from the emitting block to this node
     */
    fun incoming(conn: RsConn): OutputLinkType {

        val thisPos = conn.blockConnectedIntoPos
        val thisBs = conn.v.getBlock(thisPos).state
        val incConnOffset = conn.connDir

        val forwardDir = bsToForwardBasis(thisBs)
        val forwardOffset = forwardDir.heading
        val localIncConnOffset = alignRelDirWithRelForward(forwardOffset, incConnOffset)
        val incConnOffsetForMatching = -localIncConnOffset

        // Incredibly ugly but handle comparator exceptions
        if (incConnOffsetForMatching in listOf(RelDirs.LEFT, RelDirs.RIGHT)) {
            if (thisBs.fullName == "minecraft:comparator") {
                val originBs = conn.v.getBlock(conn.connOrigin).state
                if (originBs.fullName in COMPARATOR_EXCEPTIONS) {
                    return OutputLinkType.NONE
                }
            }
        }


        val possibleValidConns = validIncomingConns[incConnOffsetForMatching]
        // If there's no valid conns you can make to this node, return
        if (possibleValidConns == null) return OutputLinkType.NONE

        // Prioritise the ANY mapping. There should only be one if it exists but wtv
        if (possibleValidConns[RsConnType.ANY] != null) {
            return possibleValidConns[RsConnType.ANY]!!
        }

        // No any mapping, so we check every conn is any matches the incoming conns
        if (possibleValidConns[conn.connType] != null) {
            return possibleValidConns[conn.connType]!!
        }

        // No matching conn mapping found, so we return none
        return OutputLinkType.NONE
    }

    protected fun genBlockPowerConns(v: McVolume, blockPos: IVec3, hardPower: Boolean): List<RsConn> {
        val p = if (hardPower) RsConnType.HARD_POWER else RsConnType.SOFT_POWER
        val out = mutableListOf<RsConn>()
        for (d in Direction.allDirs) {
            out.add(RsConn(v, blockPos, d.heading, p, blockPos + d))
        }
        //println(" -- BLOCK POWER CONNS GENERATED")
        return out.toList()
    }

    protected fun genHardPowerInDirDirectInAllOthers(v: McVolume, thisPos: IVec3, hardPowerDir: Direction, genHardPowerDir: Boolean = true): List<RsConn> {
        val out = mutableListOf<RsConn>()
        if (genHardPowerDir) {
            out.addAll(genBlockPowerConns(v, thisPos + hardPowerDir, hardPower = true))
        }
        // # Direct power everywhere around that isn't the hard power side
        for (d in Direction.allDirs) {
            if (d == hardPowerDir) continue
            out.add(RsConn(v, thisPos, d.heading, RsConnType.DIRECT, thisPos + d))
        }
        return out.toList()
    }

    protected fun invertRelDirHorizontally(d: IVec3) = (-(d.xz)).xxy.withY(d.y)
    protected fun turnRelDirHoriz90(d: IVec3) = (d.xz.rotate90()).xxy.withY(d.y)

    fun alignRelDirWithRelForward(forward: IVec3, toAlign: IVec3): IVec3 {
        if (forward.x != 0 && forward.z != 0) {
            throw Exception("${forward} isn't a valid direction. It has to be axis aligned along XZ")
        }
        if (forward.x == 0 && forward.z == 0) {
            throw Exception("${forward} is not a valid forward vec. " +
                    "Needs to be axis aligned and pointing in a direction horizontally")
        }
        // Turn until forward actually points forward, when it does, toAlign will be too
        var (f, aligned) = forward to toAlign
        while (true) {
            if (f.z >= 1) return aligned
            f = turnRelDirHoriz90(f)
            aligned = turnRelDirHoriz90(aligned)
        }
    }
}




class RedstoneWireConns : NodeConns() {

    // Incoming from <relative dir>
    // North is the front of the redstone. A redstone wire doesnt have orientation so we have to choose one
    override val validIncomingConns = hashMapOf(
        RelDirs.UP to hashMapOf(
            RsConnType.HARD_POWER to OutputLinkType.NORMAL,
            RsConnType.DIRECT to OutputLinkType.NORMAL,
        ),
        RelDirs.DOWN to hashMapOf(
            RsConnType.HARD_POWER to OutputLinkType.NORMAL,
            RsConnType.DIRECT to OutputLinkType.NORMAL,
        ),
    )

    private val bsRedirectionMaps = mutableListOf<Pair<BlockState, Pair<HashMap<IVec3, Boolean>, BlockStateToDirFunc>>>()

    init {
        // Init valid incoming conns
        for (d in RelDirs.HORIZ) {
            validIncomingConns[d] = hashMapOf(
                RsConnType.HARD_POWER to OutputLinkType.NORMAL,
                RsConnType.DIRECT to OutputLinkType.NORMAL,
                RsConnType.DIRECT_COMPREP to OutputLinkType.NORMAL,
            )
            val downDir = d + ivec3(0, -1, 0)
            validIncomingConns[downDir] = hashMapOf(
                RsConnType.DIRECT to OutputLinkType.NORMAL
            )
            val upDir = d + ivec3(0, 1, 0)
            validIncomingConns[upDir] = hashMapOf(
                RsConnType.DIRECT to OutputLinkType.NORMAL
            )
        }

        // # Init redirectors
        // Init universal
        fun registerRedirector(bsFullName: String, front: Boolean, back: Boolean, left: Boolean, right: Boolean,
                               bsToDir: BlockStateToDirFunc
        ) {
            bsRedirectionMaps.add(
                BlockState.fromStr(bsFullName) to
                        (hashMapOf(
                            RelDirs.FORWARD to front,
                            RelDirs.BACKWARD to back,
                            RelDirs.LEFT to left,
                            RelDirs.RIGHT to right,
                        ) to bsToDir )
            )
        }
        fun registerUniversalRedirector(bsFullName: String) {
            registerRedirector(bsFullName, true, true, true, true) { bs -> Direction.NORTH }
        }
        registerUniversalRedirector("minecraft:stone_button")
        registerUniversalRedirector("minecraft:redstone_wire")
        registerUniversalRedirector("minecraft:stone_pressure_plate")
        registerUniversalRedirector("minecraft:target")
        registerUniversalRedirector("minecraft:comparator")
        registerUniversalRedirector("minecraft:tripwire_hook")
        registerUniversalRedirector("minecraft:redstone_torch")
        registerUniversalRedirector("minecraft:redstone_wall_torch")

        registerRedirector("minecraft:repeater", true, true, false, false) { bs ->
            Direction.fromProp(bs.getProp("facing").orElseThrow { throw Exception("repeater no facing prop") }).opposite
        }
        registerRedirector("minecraft:observer", false, true, false, false) { bs ->
            Direction.fromProp(bs.getProp("facing").orElseThrow { throw Exception("observer no facing prop") }).opposite
        }
    }

    override fun outgoing(v: McVolume, thisPos: IVec3): List<RsConn> {

        // ## Get true redirections of this redstone
        val origBs = v.getBlock(thisPos).state
        // # Get forced directions
        val forcedRedirects = hashMapOf(
            Direction.EAST to false,
            Direction.NORTH to false,
            Direction.WEST to false,
            Direction.SOUTH to false,
        )
        for ((d, _) in forcedRedirects) {
            val adjBs = v.getBlock(thisPos + d).state

            val adjRedirectMapData = bsRedirectionMaps.firstOrNull { v -> adjBs.looselyMatches(v.first) }
            // If there is a redirector directly this way
            if (adjRedirectMapData != null) {
                // Get the relative forward for this blockstate
                val adjBsForward = adjRedirectMapData.second.second(adjBs)
                // Our current direction assume forward is Z+, so we rotate it until its forward is our adjBs' forward
                val localBsDir = alignRelDirWithRelForward(adjBsForward.heading, d.heading)
                // Matching sides need some inverting
                val localBsDirForMatching = -localBsDir

                // If this local direction's map says it does redirect on this side, then add it to the forced redirects
                val adjRedirectMap = adjRedirectMapData.second.first
                //println(localBsDirForMatching)
                if (adjRedirectMap[localBsDirForMatching]!! == true) {
                    forcedRedirects[d] = true
                    continue
                }
            }
            // Handle dust up diag redirection
            val diagUp = isThereConnectedDustDiagThisDir(v, thisPos, d, up = true)
            if (diagUp != null) {
                forcedRedirects[d] = true
            }
            val diagDown = isThereConnectedDustDiagThisDir(v, thisPos, d, up = false)
            if (diagDown != null) {
                forcedRedirects[d] = true
            }
        }
        // # Get the actual direction the redstone is pointing to
        val trueRedirects = HashMap(forcedRedirects)
        val forcedRedirsCount = forcedRedirects.count { (d, v) -> v }
        if (forcedRedirsCount == 0) {
            // If forced to point to no direction, its true redirection is determined by its
            // block state, whether its a dot or not
            val notDot = Direction.cardinal.any {
                origBs.getProp(it.propVal).orElse("none") != "none"
            }
            if (notDot) {
                // Then cross, so we redir everywhere
                for (d in Direction.cardinal) trueRedirects[d] = true
            } else {
                // Dot. redirects already all false
            }
        }
        if (forcedRedirsCount == 1) {
            // Make it point the opposite way it's facing too
            for (d in Direction.cardinal) {
                if (trueRedirects[d] == true) {
                    trueRedirects[d.opposite] = true
                    break // Break to not do more unnecessary setting
                }
            }
        }

        // ## Conn time
        val outConns = mutableListOf<RsConn>()


        // Redstone tries to connect to all 4 sides
        for (d in Direction.cardinal) {
            outConns.add(RsConn(v, thisPos, d.heading, RsConnType.DIRECT, thisPos + d))
        }
        // Redstone soft powers all the blocks it points to
        for ((d, isDirected) in trueRedirects) {
            if (isDirected) {
                val blockPointedAtPos = thisPos + d
                val blockPointedAt = v.getBlock(blockPointedAtPos).state
                if (BsHelper.isConductive(blockPointedAt)) {
                    outConns.addAll(genBlockPowerConns(v, blockPointedAtPos, hardPower = false))
                }
            }
        }
        // Redstone softpowers the block below it
        val blockBelowPos = thisPos + Direction.DOWN
        val belowBlock = v.getBlock(blockBelowPos).state
        if (BsHelper.isConductive(belowBlock)) {
            outConns.addAll(genBlockPowerConns(v, blockBelowPos, hardPower = false))
        }
        // Redstone can go diagonally up or down
        for (d in Direction.cardinal) {
            val diagUpPos = isThereConnectedDustDiagThisDir(v, thisPos, d, up = true)
            if (diagUpPos != null) {
                outConns.add(RsConn(v, thisPos, diagUpPos - thisPos, RsConnType.DIRECT, diagUpPos))
            }
            val diagDownPos = isThereConnectedDustDiagThisDir(v, thisPos, d, up = false)
            if (diagDownPos != null) {
                outConns.add(RsConn(v, thisPos, diagDownPos - thisPos, RsConnType.DIRECT, diagDownPos))
            }
        }

        return outConns.toList()
    }

    private fun isThereConnectedDustDiagThisDir(v: McVolume, origPos: IVec3, dir: Direction, up: Boolean = false): IVec3? {
        if (up == true) {
            // Diag up case
            val posAboveRs = origPos + Direction.UP
            val blockAboveRs = v.getBlock(posAboveRs).state
            val potentialRsDiagUpPos = posAboveRs + dir
            val potentialRsDiagUpBs = v.getBlock(potentialRsDiagUpPos).state
            if (BsHelper.isRsTransparent(blockAboveRs) && potentialRsDiagUpBs.fullName == "minecraft:redstone_wire") {
                return potentialRsDiagUpPos
            }
            return null
        } else {
            // Diag down case
            val posBelowRs = origPos + Direction.DOWN
            val blockBelowRs = v.getBlock(posBelowRs).state
            val potentialRsDiagDownPos = posBelowRs + dir
            val potentialRsDiagDownBs = v.getBlock(potentialRsDiagDownPos).state
            val adjBs = v.getBlock(origPos + dir).state
            if (BsHelper.isConductive(blockBelowRs) &&
                BsHelper.isRsTransparent(adjBs) &&
                potentialRsDiagDownBs.fullName == "minecraft:redstone_wire")
            {
                return potentialRsDiagDownPos
            }
            return null
        }
    }

    override fun bsToForwardBasis(thisBs: BlockState): Direction {
        if (thisBs.fullName != "minecraft:redstone_wire") {
            throw Exception("Inputted blockstate isn't of the correct state for the redstone wire 'node'")
        }
        return Direction.NORTH
    }

}



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
            outConns.addAll(genBlockPowerConns(v, forwardPos, hardPower = true))
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
        // Torch powering directly to the sides
        for (d in Direction.cardinal) {
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



class ComparatorConns : NodeConns() {

    // Incoming from <relative dir>
    override val validIncomingConns = hashMapOf(
        RelDirs.RIGHT to hashMapOf(
            RsConnType.DIRECT to OutputLinkType.SIDE,
            RsConnType.DIRECT_COMPREP to OutputLinkType.SIDE,
        ),
        RelDirs.LEFT to hashMapOf(
            RsConnType.DIRECT to OutputLinkType.SIDE,
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

        // Comparator powering directly in front of it
        outConns.add(RsConn(v, thisPos, forward.heading, RsConnType.DIRECT_COMPREP, thisPos + forward))
        // Comparator hard powering in front of it
        if (BsHelper.isConductive(forwardBs)) {
            outConns.addAll(genBlockPowerConns(v, forwardPos, hardPower = true))
        }

        return outConns.toList()
    }


    override fun bsToForwardBasis(thisBs: BlockState): Direction {
        if (thisBs.fullName != "minecraft:comparator") {
            throw Exception("Inputted blockstate isn't of the correct state for the comparator node")
        }
        val propOption = thisBs.getProp("facing")
        if (propOption.isEmpty) {
            throw Exception("No prop 'facing' in the inputted blockstate")
        }
        return Direction.fromProp(thisBs.getProp("facing").get()).opposite
    }

}





class LeverConns : NodeConns() {
    // Don't connect to levers
    override val validIncomingConns: HashMap<IVec3, HashMap<RsConnType, OutputLinkType>> = hashMapOf()

    override fun bsToForwardBasis(thisBs: BlockState): Direction {
        val face = getFace(thisBs)
        if (face == "wall") {
            return Direction.fromProp(getFacing(thisBs)).opposite
        } else {
            return Direction.NORTH
        }
    }

    private fun getFace(thisBs: BlockState) = thisBs.getProp("face").orElse("wall")
    private fun getFacing(thisBs: BlockState) = thisBs.getProp("facing").orElse("north")

    override fun outgoing(v: McVolume, thisPos: IVec3): List<RsConn> {
        val thisBs = v.getBlock(thisPos).state
        val outConns = mutableListOf<RsConn>()

        // Get hard power direction
        val face = getFace(thisBs)
        val powerDir = if (face == "floor") {
            Direction.DOWN
        } else if (face == "ceiling") {
            Direction.UP
        } else {
            Direction.fromProp(getFacing(thisBs)).opposite
        }
        // Get whether to hard power
        val bsHardPoweredInto = v.getBlock(thisPos + powerDir).state
        val doHardPower = BsHelper.isConductive(bsHardPoweredInto)

        // Gen conns
        outConns.addAll(genHardPowerInDirDirectInAllOthers(v, thisPos, powerDir, doHardPower))
        outConns.add(RsConn(v, thisPos, powerDir.heading, RsConnType.DIRECT, thisPos + powerDir))

        return outConns
    }
}






class ButtonConns : NodeConns() {
    // Don't connect to buttons
    override val validIncomingConns: HashMap<IVec3, HashMap<RsConnType, OutputLinkType>> = hashMapOf()

    override fun bsToForwardBasis(thisBs: BlockState): Direction {
        val face = getFace(thisBs)
        if (face == "wall") {
            return Direction.fromProp(getFacing(thisBs)).opposite
        } else {
            return Direction.NORTH
        }
    }

    private fun getFace(thisBs: BlockState) = thisBs.getProp("face").orElse("wall")
    private fun getFacing(thisBs: BlockState) = thisBs.getProp("facing").orElse("north")

    override fun outgoing(v: McVolume, thisPos: IVec3): List<RsConn> {
        val thisBs = v.getBlock(thisPos).state
        val outConns = mutableListOf<RsConn>()

        // Get hard power direction
        val face = getFace(thisBs)
        val powerDir = if (face == "floor") {
            Direction.DOWN
        } else if (face == "ceiling") {
            Direction.UP
        } else {
            Direction.fromProp(getFacing(thisBs)).opposite
        }
        // Get whether to hard power
        val bsHardPoweredInto = v.getBlock(thisPos + powerDir).state
        val doHardPower = BsHelper.isConductive(bsHardPoweredInto)

        // Gen conns
        outConns.addAll(genHardPowerInDirDirectInAllOthers(v, thisPos, powerDir, doHardPower))
        outConns.add(RsConn(v, thisPos, powerDir.heading, RsConnType.DIRECT, thisPos + powerDir))

        return outConns
    }
}



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




class RedstoneLampConns : NodeConns() {
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



