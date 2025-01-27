package com.sloimay.threadstonecore.redstoneir.conns

import com.sloimay.threadstonecore.redstoneir.*
import com.sloimay.threadstonecore.redstoneir.from.Direction
import com.sloimay.threadstonecore.redstoneir.from.plus
import me.sloimay.mcvolume.McVolume
import me.sloimay.mcvolume.block.BlockState
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
    BlockState.fromStr("minecraft:wooden_button") to ButtonConns(),
    BlockState.fromStr("minecraft:stone_pressure_plate") to PressurePlateConns(),
    BlockState.fromStr("minecraft:wooden_pressure_plate") to PressurePlateConns(),
    BlockState.fromStr("minecraft:light_weighted_pressure_plate") to PressurePlateConns(),
    BlockState.fromStr("minecraft:heavy_weighted_pressure_plate") to PressurePlateConns(),
    BlockState.fromStr("minecraft:redstone_block") to RedstoneBlockConns(),
    BlockState.fromStr("minecraft:redstone_lamp") to LampConns(),
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