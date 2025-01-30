package com.sloimay.threadstonecore.redstoneir.from

import com.sloimay.threadstonecore.redstoneir.RedstoneBuildIR
import com.sloimay.threadstonecore.redstoneir.conns.BS_TO_CONNS
import com.sloimay.threadstonecore.redstoneir.conns.OutputLinkType
import com.sloimay.threadstonecore.redstoneir.helpers.BsHelper
import com.sloimay.threadstonecore.redstoneir.helpers.ComparatorCompileHelper
import com.sloimay.threadstonecore.redstoneir.rsirnodes.*
import com.sloimay.threadstonecore.redstoneir.rsirnodes.special.RsIrRenderedWire
import com.sloimay.threadstonecore.redstoneir.rsirnodes.special.RsIrRenderedWireInput
import me.sloimay.mcvolume.McVolume
import me.sloimay.mcvolume.block.BlockState
import me.sloimay.smath.vectors.IVec3
import me.sloimay.smath.vectors.ivec3
import me.sloimay.smath.vectors.swizzles.xxy
import me.sloimay.smath.vectors.swizzles.xz


data class Direction internal constructor(val heading: IVec3, val propVal: String) {

    companion object {
        val NORTH = Direction(ivec3(0, 0, -1), "north")
        val SOUTH = Direction(ivec3(0, 0, 1), "south")
        val EAST = Direction(ivec3(1, 0, 0), "east")
        val WEST = Direction(ivec3(-1, 0, 0), "west")
        val UP = Direction(ivec3(0, 1, 0), "up")
        val DOWN = Direction(ivec3(0, -1, 0), "down")

        val cardinal = listOf(EAST, SOUTH, WEST, NORTH)

        val allDirs = listOf(NORTH, SOUTH, EAST, WEST, UP, DOWN)

        fun fromProp(prop: String): Direction {
            for (d in allDirs) if (d.propVal == prop) return d
            throw Exception("Invalid prop string")
        }

        fun new(heading: IVec3): Direction {
            for (d in allDirs) if (d.heading.equality(heading)) return d
            throw Exception("Invalid heading vector")
        }
    }

    val right get() = new(heading.xz.rotate90().xxy.withY(heading.y))
    val left get() = new(heading.xz.rotateM90().xxy.withY(heading.y))

    val opposite get() = new(-heading)

    override fun hashCode() = this.heading.hashCode()

    fun eq(other: Direction) = this.heading == other.heading

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Direction

        if (heading != other.heading) return false

        return true
    }

}

operator fun IVec3.plus(dir: Direction) = this + dir.heading

fun IVec3.up() = this + Direction.UP
fun IVec3.down() = this + Direction.DOWN
fun IVec3.north() = this + Direction.NORTH
fun IVec3.south() = this + Direction.SOUTH
fun IVec3.east() = this + Direction.EAST
fun IVec3.west() = this + Direction.WEST







/*fun RsGraph.Companion.fromSchem(fp: String): GraphFromResult {
    val v = McVolume.fromSchem(fp)
    val gr = RsGraph.fromVolume(v)
    return gr
}*/


fun RedstoneBuildIR.Companion.fromVolume(v: McVolume): RedstoneBuildIR {

    /**
     *
     * Guarantees to keep:
     *  - Before graph optimisation at the end, *every* node should have a position. As the
     *    unoptimized IR is meant to represent the redstone circuit exactly.
     *
     */



    v.expandLoadedArea(ivec3(20, 20, 20))
    val buildBounds = v.getBuildBounds()

    // # Identify nodes
    val compHasDirectSsRead = hashSetOf<IVec3>()
    val renderingRsWires = hashMapOf<IVec3, RsIrRenderedWire>()
    //val userInputNodes = hashMapOf<IVec3, RsIrInputNode>()
    val blockNodes = hashMapOf<IVec3, RsIrNode>()
    for (pos in buildBounds.iterYzx()) {
        val b = v.getBlock(pos)
        val name = b.state.fullName
        when {
            name == "minecraft:redstone_torch" || name == "minecraft:redstone_wall_torch" -> {
                blockNodes[pos] = RsIrTorch(
                    v,
                    pos,
                    b.state.getProp("lit").orElse("false") == "true"
                )
            }
            name == "minecraft:repeater" -> {
                val realDelay = b.state.getProp("delay").orElse("1").toInt()
                blockNodes[pos] = RsIrRepeater(
                    v,
                    pos,
                    realDelay,
                    b.state.getProp("powered").orElse("false") == "true",
                    b.state.getProp("locked").orElse("false") == "true",
                )
            }
            name == "minecraft:comparator" -> {
                // Get output data
                val compNbt = v.getTileData(pos)
                var compPowerOut = 0
                if (compNbt != null) {
                    val outputSignalKey = "OutputSignal"
                    if (compNbt.containsKey(outputSignalKey)) {
                        compPowerOut = compNbt.getInt(outputSignalKey)
                    }
                }
                // # Get if there's a readable block behind the comparator, add a constant node to it
                val compDir = Direction.fromProp(b.state.getProp("facing").orElse("east")).opposite
                val directionBehind = compDir.opposite
                val posBehind = pos + directionBehind
                val bsBehind = v.getBlock(posBehind).state
                var constantNode: RsIrConstant? = null
                if (ComparatorCompileHelper.isSsReadable(bsBehind)) {
                    val ss = ComparatorCompileHelper.readSs(v, posBehind)
                    constantNode = RsIrConstant(v, posBehind, ss)
                    // Mark this comparator as having a direct ss read
                    compHasDirectSsRead.add(pos)
                }
                // # Get far input if there is one
                var farInputSs: Int? = null
                if (pos !in compHasDirectSsRead) {
                    if (BsHelper.isConductive(bsBehind)) {
                        val posBehindBehind = posBehind + directionBehind
                        val bsBehindBehind = v.getBlock(posBehindBehind).state
                        if (ComparatorCompileHelper.isSsReadable(bsBehindBehind)) {
                            val ss = ComparatorCompileHelper.readSs(v, posBehindBehind)
                            farInputSs = ss
                        }
                    }
                }

                // # Add comp node
                blockNodes[pos] = RsIrComparator(
                    v,
                    pos,
                    RsIrCompMode.fromProp(b.state.getProp("mode").orElse("compare")),
                    farInputSs ?: -1,
                    compPowerOut,
                )
                // # Add the direct back ss input constant node if there is one
                if (constantNode != null) {
                    // Check that the node hasn't already been added.
                    if (posBehind !in blockNodes) {
                        blockNodes[pos]!!.addInput(RsIrBackwardLink(constantNode, 0, BackwardLinkType.NORMAL))
                        blockNodes[posBehind] = constantNode
                    } else {
                        // If it has already been compiled, don't add the curr constant node
                        // but the one already present
                        blockNodes[pos]!!.addInput(RsIrBackwardLink(blockNodes[posBehind]!!, 0, BackwardLinkType.NORMAL))
                    }
                }
            }
            name == "minecraft:redstone_block" -> {
                blockNodes.putIfAbsent(pos, RsIrConstant(v, pos, 15))
            }
            name == "minecraft:redstone_lamp" -> {
                blockNodes[pos] = RsIrLamp(v, pos, b.state.getProp("lit").orElse("false") == "true")
            }
            name == "minecraft:lever" -> {
                blockNodes[pos] = RsIrLever(v, pos, b.state.getProp("powered").orElse("false") == "true")
            }
            name == "minecraft:stone_button" -> {
                blockNodes[pos] = RsIrStoneButton(v, pos, b.state.getProp("powered").orElse("false") == "true")
            }
            name == "minecraft:stone_pressure_plate" -> {
                blockNodes[pos] = RsIrStonePressurePlate(v, pos, b.state.getProp("powered").orElse("false") == "true")
            }
        }
    }


    /*for ((nodePos, node) in blockNodes) {
        println("$nodePos, $node")
        for (i in node.getInputs()) {
            println("   ${i.node} ${i.node.position}")
        }
    }*/


    // For each node, BFS forward and stop at components that aren't redstone wires
    for ((startNodePos, node) in blockNodes) {
        val startNodeBs = node.parentVol.getBlock(node.position!!).state
        val startNode = node

        //println("======== BFS start")

        // Add 1 to redstoneDist *only* when the emitting block is a redstone_wire
        data class BfsData(val pos: IVec3, val bs: BlockState, val redstoneDist: Int) {
            operator fun component0() = pos
        }
        val visited = hashSetOf<IVec3>()
        val q = ArrayDeque<BfsData>()

        // # Start BFS
        q.add(BfsData(startNodePos, startNodeBs, 0))
        while (q.isNotEmpty()) {
            val (currNodePos, currBs, currRsDist) = q.removeFirst()
            // Don't go over nodes we've already visited
            if (currNodePos in visited) continue
            // Visit other nodes as many times as you want, but not redstone wires
            //if (currBs.fullName == "minecraft:redstone_wire") {
            visited.add(currNodePos)
            //}

            // # Compile rendered rs wires: if here is redstone, then add
            // Add a rendering redstone wire """node"""
            if (currBs.fullName == "minecraft:redstone_wire") {
                if (currNodePos !in renderingRsWires) {
                    renderingRsWires[currNodePos] = RsIrRenderedWire(v, currNodePos, mutableListOf())
                    //println("rs wire compiled at $currNodePos")
                }
                renderingRsWires[currNodePos]!!.addInput(RsIrRenderedWireInput(startNode, currRsDist))
            }

            // # Do conn attempts
            val currBsToBsConns = BS_TO_CONNS.firstOrNull { b -> currBs.looselyMatches(b.first) }
            if (currBsToBsConns == null) continue
            val currBsConns = currBsToBsConns.second

            val connAttempts = currBsConns.outgoing(v, currNodePos)
            //println("CONN ATTEMPTS FOR BLOCK $currBs AT $currNodePos:")

            for (connAttempt in connAttempts) {
                val connOutputBlockPos = connAttempt.blockConnectedIntoPos
                val connOutputBs = v.getBlock(connOutputBlockPos).state
                //println(connAttempt)
                // If this pointed block at is not a node (and it's not redstone), don't pursue this attempt
                if (!blockNodes.containsKey(connOutputBlockPos) && connOutputBs.fullName != "minecraft:redstone_wire") continue
                val thisConnNodeEntry = blockNodes[connOutputBlockPos]

                val bsToOutputBlockConns = BS_TO_CONNS.firstOrNull { b -> connOutputBs.looselyMatches(b.first) }
                // If there's no block to connect to, then there's nothing to do, this attempt failed
                if (bsToOutputBlockConns == null) continue
                val outputBlockConns = bsToOutputBlockConns.second
                val outConnType = outputBlockConns.incoming(connAttempt)
                when (outConnType) {
                    OutputLinkType.NORMAL -> {
                        // If the bs we're going through in the BFS rn is not redstone but we're connecting
                        // into a redstone, continue the bfs search but keep the redstone dist at 0
                        //println("Conn output link normal found:")
                        //println("Curr bs: $currBs")
                        //println("outputBlockPos: $connOutputBlockPos")
                        //println("outputBlockBs: $connOutputBs")
                        if (currBs.fullName != "minecraft:redstone_wire") {
                            if (connOutputBs.fullName == "minecraft:redstone_wire") {
                                q.add(BfsData(connOutputBlockPos, v.getBlock(connOutputBlockPos).state, 0))
                            }
                        } else {

                            if (connOutputBs.fullName == "minecraft:redstone_wire") {
                                // If we strayed too far from the power source, don't continue BFS-ing
                                if (currRsDist < 14) {
                                    q.add(BfsData(connOutputBlockPos, v.getBlock(connOutputBlockPos).state, currRsDist + 1))
                                }
                            }
                        }

                        // If we're about to add an input to a comparator, check if it already has a direct ss input.
                        // If not, we're free to go ahead
                        if (thisConnNodeEntry != null) {
                            if (connOutputBs.fullName == "minecraft:comparator") {
                                if (connOutputBlockPos !in compHasDirectSsRead) {
                                    thisConnNodeEntry.addInput(RsIrBackwardLink(startNode, currRsDist, BackwardLinkType.NORMAL))
                                    startNode.addOutput(RsIrForwardLink(thisConnNodeEntry, currRsDist, ForwardLinkType.NORMAL))
                                }
                            } else {
                                thisConnNodeEntry.addInput(RsIrBackwardLink(startNode, currRsDist, BackwardLinkType.NORMAL))
                                startNode.addOutput(RsIrForwardLink(thisConnNodeEntry, currRsDist, ForwardLinkType.NORMAL))
                            }
                        }
                    }
                    OutputLinkType.SIDE -> {
                        if (thisConnNodeEntry != null) {
                            thisConnNodeEntry.addInput(RsIrBackwardLink(startNode, currRsDist, BackwardLinkType.SIDE))
                            startNode.addOutput(RsIrForwardLink(thisConnNodeEntry, currRsDist, ForwardLinkType.NORMAL))
                        }

                    }
                    OutputLinkType.NONE -> continue // Conn unsuccessful
                }
            }
        }
    }


    /*val graph = RsGraph()
    for ((nodePos, nodeData) in blockNodes) {
        val (bs, node) = nodeData
        graph.addNode(node)
    }

    val nodePositions = HashMap<RsNode, IVec3>()
    for ((pos, data) in blockNodes) nodePositions[data.second] = pos
    */
    /*
    for (n in graph.nodes) {
        println("node at ${nodePositions[n]!!}: ${n}}")
        for (i in n.inputs) {
            println("  $i")
            println("    - at position ${nodePositions[i.node]!!}")
        }
    }*/

    //return Pair(graph, nodePositions)
    //return GraphFromResult(graph, nodePositions, v, renderingRsWires, userInputNodes)

    val graph = RedstoneBuildIR(v)
    for ((nodePos, node) in blockNodes) {
        graph.addNode(node)
    }
    for ((pos, rsw) in renderingRsWires) {
        graph.addRenderedRsWire(rsw)
    }

    graph.finalizeAllNodeAddition()

    //graph.optimise(ioOnly = true)

    return graph
}