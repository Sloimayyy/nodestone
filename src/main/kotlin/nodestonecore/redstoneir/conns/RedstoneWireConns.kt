package com.sloimay.nodestonecore.redstoneir.conns

import com.sloimay.mcvolume.McVolume
import com.sloimay.mcvolume.block.BlockState
import com.sloimay.smath.vectors.IVec3
import com.sloimay.smath.vectors.ivec3
import com.sloimay.nodestonecore.redstoneir.from.Direction
import com.sloimay.nodestonecore.redstoneir.from.plus
import com.sloimay.nodestonecore.redstoneir.helpers.BsHelper

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

            val adjRedirectMapData = bsRedirectionMaps
                .firstOrNull { v -> adjBs.looselyMatches(v.first) }
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
            val (diagUp, canPowerDiagUp) = isThereConnectedDustDiagThisDir(v, thisPos, d, dirIsUp = true)
            if (diagUp != null) {
                forcedRedirects[d] = true
            }
            val (diagDown, canPowerDiagDown) = isThereConnectedDustDiagThisDir(v, thisPos, d, dirIsUp = false)
            if (diagDown != null) {
                forcedRedirects[d] = true
            }
        }

        /*println("forced redirects of $thisPos")
        for ((d, t) in forcedRedirects)
            println("$d, $t")*/

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

        /*println("true redirects of $thisPos")
        for ((d, t) in trueRedirects)
            println("$d, $t")*/

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
            val (diagUpPos, canPowerDiagUp) = isThereConnectedDustDiagThisDir(v, thisPos, d, dirIsUp = true)
            if (diagUpPos != null && canPowerDiagUp) {
                outConns.add(RsConn(v, thisPos, diagUpPos - thisPos, RsConnType.DIRECT, diagUpPos))
            }
            val (diagDownPos, canPowerDiagDown) = isThereConnectedDustDiagThisDir(v, thisPos, d, dirIsUp = false)
            if (diagDownPos != null && canPowerDiagDown) {
                outConns.add(RsConn(v, thisPos, diagDownPos - thisPos, RsConnType.DIRECT, diagDownPos))
            }
        }
        // Redstone sends a direct conn underneath it
        outConns.add(RsConn(v, thisPos, RelDirs.DOWN, RsConnType.DIRECT, thisPos + RelDirs.DOWN))

        return outConns.toList()
    }

    private fun isThereConnectedDustDiagThisDir(v: McVolume, origPos: IVec3, dir: Direction, dirIsUp: Boolean = false): Pair<IVec3?, Boolean> {
        if (dirIsUp) {
            // Diag up case
            val posAboveRs = origPos + Direction.UP
            val blockAboveRs = v.getBlock(posAboveRs).state
            val potentialRsDiagUpPos = posAboveRs + dir
            val potentialRsDiagUpBs = v.getBlock(potentialRsDiagUpPos).state
            if (BsHelper.isRsTransparent(blockAboveRs) && potentialRsDiagUpBs.fullName == "minecraft:redstone_wire") {
                return potentialRsDiagUpPos to true
            }
            return null to false
        } else {
            // Diag down case
            val posBelowRs = origPos + Direction.DOWN
            val blockBelowBs = v.getBlock(posBelowRs).state
            val potentialRsDiagDownPos = posBelowRs + dir
            val potentialRsDiagDownBs = v.getBlock(potentialRsDiagDownPos).state
            val adjBs = v.getBlock(origPos + dir).state
            if (BsHelper.isRsTransparent(adjBs) && potentialRsDiagDownBs.fullName == "minecraft:redstone_wire") {
                if (BsHelper.isConductive(blockBelowBs)) {
                    return potentialRsDiagDownPos to true
                } else {
                    return potentialRsDiagDownPos to false
                }
            } else {
                return null to false
            }
        }
    }

    override fun bsToForwardBasis(thisBs: BlockState): Direction {
        if (thisBs.fullName != "minecraft:redstone_wire") {
            throw Exception("Inputted blockstate isn't of the correct state for the redstone wire 'node'")
        }
        return Direction.NORTH
    }

}