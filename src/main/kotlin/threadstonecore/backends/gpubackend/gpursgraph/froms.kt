package com.sloimay.threadstonecore.backends.gpubackend.gpursgraph

import com.sloimay.threadstonecore.backends.gpubackend.gpursgraph.nodes.*
import me.sloimay.mcvolume.McVolume
import me.sloimay.mcvolume.block.BlockState
import me.sloimay.mcvolume.io.fromSchem
import me.sloimay.shadestone.simulator.graph.node.*
import com.sloimay.threadstonecore.helpers.BsHelper
import com.sloimay.threadstonecore.helpers.ComparatorGraphCompileHelper
import me.sloimay.shadestone.toInt
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




class RenderedRsWireInput(val node: RsNode, val dist: Int)
class RenderedRsWire(val inputs: MutableList<RenderedRsWireInput>)



class GraphFromResult(val graph: RsGraph,
                      val nodePositions: HashMap<RsNode, IVec3>,
                      val volume: McVolume,
                      val renderedRsWires: HashMap<IVec3, RenderedRsWire>,
                      val inputNodes: HashMap<IVec3, UserInputNode>)




fun RsGraph.Companion.fromSchem(fp: String): GraphFromResult {
    val v = McVolume.fromSchem(fp)
    val gr = RsGraph.fromVolume(v)
    return gr
}


fun RsGraph.Companion.fromVolume(v: McVolume): GraphFromResult {
    v.expandLoadedArea(ivec3(20, 20, 20))
    val buildBounds = v.getBuildBounds()

    // # Identify nodes

    val compHasDirectSsRead = hashSetOf<IVec3>()

    val renderingRsWires = hashMapOf<IVec3, RenderedRsWire>()

    val userInputNodes = hashMapOf<IVec3, UserInputNode>()

    val blockNodes = hashMapOf<IVec3, Pair<BlockState, RsNode>>()
    for (pos in buildBounds.iterYzx()) {
        val b = v.getBlock(pos)
        val name = b.state.fullName
        when {
            name == "minecraft:redstone_torch" || name == "minecraft:redstone_wall_torch" -> {
                blockNodes[pos] = b.state to TorchNode(b.state.getProp("lit").orElse("false") == "true")
            }
            name == "minecraft:repeater" -> {
                val realDelay = b.state.getProp("delay").orElse("1").toInt()
                val schedulerMask = (1 shl (realDelay)) - 1
                val schedulerBits = -(b.state.getProp("powered").orElse("false") == "true").toInt() and schedulerMask
                blockNodes[pos] = b.state to RepeaterNode(
                    b.state.getProp("locked").orElse("false") == "true",
                    realDelay,
                    schedulerBits,
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
                var compDir = Direction.fromProp(b.state.getProp("facing").orElse("east")).opposite
                val directionBehind = compDir.opposite
                val posBehind = pos + directionBehind
                val bsBehind = v.getBlock(posBehind).state
                var constantNode: ConstantNode? = null
                if (ComparatorGraphCompileHelper.isSsReadable(bsBehind)) {
                    val ss = ComparatorGraphCompileHelper.readSs(v, posBehind)
                    constantNode = ConstantNode(ss)
                    // Mark this comparator as having a direct ss read
                    compHasDirectSsRead.add(pos)
                }
                // # Get far input if there is one
                var farInputSs: Int? = null
                if (pos !in compHasDirectSsRead) {
                    if (BsHelper.isConductive(bsBehind)) {
                        val posBehindBehind = posBehind + directionBehind
                        val bsBehindBehind = v.getBlock(posBehindBehind).state
                        if (ComparatorGraphCompileHelper.isSsReadable(bsBehindBehind)) {
                            val ss = ComparatorGraphCompileHelper.readSs(v, posBehindBehind)
                            farInputSs = ss
                        }
                    }
                }

                // # Add comp node
                blockNodes[pos] = b.state to ComparatorNode(
                    compPowerOut,
                    farInputSs != null,
                    farInputSs ?: 0,
                    b.state.getProp("mode").orElse("compare") == "subtract",
                )
                // # Add the direct back ss input constant node if there is one
                if (constantNode != null) {
                    // Check that the node hasn't already been added.
                    if (!blockNodes.containsKey(posBehind)) {
                        blockNodes[pos]!!.second.addInput(constantNode, 0, false)
                        blockNodes[posBehind] = bsBehind to constantNode
                    } else {
                        // If it has already been compiled, don't add the curr constant node
                        // but the one already present
                        blockNodes[pos]!!.second.addInput(blockNodes[posBehind]!!.second, 0, false)
                    }
                }
            }
            name in listOf(
                "minecraft:lever",
                "minecraft:stone_button",
                "minecraft:stone_pressure_plate",
            ) -> {
                val powered = b.state.getProp("powered").orElse("false") == "true"
                val node = UserInputNode(
                    if (powered) 15 else 0
                )
                blockNodes[pos] = b.state to node
                userInputNodes[pos] = node
            }
            name == "minecraft:redstone_block" -> {
                blockNodes[pos] = b.state to ConstantNode(15)
            }
            name == "minecraft:redstone_lamp" -> {
                blockNodes[pos] = b.state to LampNode(
                    b.state.getProp("lit").orElse("false") == "true"
                )
            }
        }
    }


    // For each node, BFS forward and stop at components that aren't redstone wires
    for ((startNodePos, nodeData) in blockNodes) {
        val (startNodeBs, startNode) = nodeData
        //if (startNode is ConstantNode) continue // Constant nodes get attributed manually

        //println("======== BFS start")

        //Add 1 to redstoneDist *only* when the emitting block is a redstone_wire
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
                    renderingRsWires[currNodePos] = RenderedRsWire(mutableListOf())
                    //println("rs wire compiled at $currNodePos")
                }
                renderingRsWires[currNodePos]!!.inputs.add(RenderedRsWireInput(startNode, currRsDist))
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
                                    thisConnNodeEntry.second.addInput(startNode, currRsDist, side = false)
                                }
                            } else {
                                thisConnNodeEntry.second.addInput(startNode, currRsDist, side = false)
                            }
                        }
                    }
                    OutputLinkType.SIDE -> thisConnNodeEntry?.second?.addInput(startNode, currRsDist, side = true)
                    OutputLinkType.NONE -> continue // Conn unsuccessful
                }
            }
        }
    }


    val graph = RsGraph()
    for ((nodePos, nodeData) in blockNodes) {
        val (bs, node) = nodeData
        graph.addNode(node)
    }

    val nodePositions = HashMap<RsNode, IVec3>()
    for ((pos, data) in blockNodes) nodePositions[data.second] = pos

    /*
    for (n in graph.nodes) {
        println("node at ${nodePositions[n]!!}: ${n}}")
        for (i in n.inputs) {
            println("  $i")
            println("    - at position ${nodePositions[i.node]!!}")
        }
    }*/

    //return Pair(graph, nodePositions)
    return GraphFromResult(graph, nodePositions, v, renderingRsWires, userInputNodes)
}




















/*fun RsGraph.Companion.fromVolume(v: McVolume): Pair<RsGraph, HashMap<RsNode, IVec3>> {
    v.expandLoadedArea(ivec3(20, 20, 20))

    val buildBounds = v.getBuildBounds()

    //val bb = IntBoundary.new(buildBounds.a, buildBounds.b + 1)



    // Identify nodes
    val blockNodes = hashMapOf<IVec3, Pair<BlockState, RsNode>>()
    for (pos in buildBounds.iterYzx()) {
        val b = v.getBlock(pos)
        val name = b.state.fullName
        when {
            // TODO: add repeaters, lamps and comparators
            name == "minecraft:redstone_torch" || name == "minecraft:redstone_wall_torch" -> {
                blockNodes[pos] = b.state to TorchNode(b.state.getProp("lit").orElse("false") == "true")
            }
        }
    }



    fun powerIntoBlockFindNextNodes(v: McVolume, poweredBp: IVec3, hardPower: Boolean): List<Pair<IVec3, BlockState>> {
        val out = mutableListOf<Pair<IVec3, BlockState>>()

        // Check the 4 horizontally adjacent blocks
        for ((idx, d) in Direction.cardinal.withIndex()) {
            val bp = poweredBp + d
            val bs = v.getBlock(bp).state

            fun addThis() = out.add(bp to bs)

            when {
                hardPower && (bs.fullName == "minecraft:redstone_wire") -> addThis()
                bs.looselyMatches(outgoingRepMatches[idx]) -> addThis()
                bs.looselyMatches(wallTorchMatches[idx]) -> addThis()
                bs.looselyMatches(outgoingComparatorMatches[idx]) -> addThis()
                bs.fullName == "minecraft:redstone_lamp" -> addThis()
            }
        }

        // Check above
        run {
            val bp = poweredBp.up()
            val bs = v.getBlock(bp).state
            fun addThis() = out.add(bp to bs)
            when {
                hardPower && (bs.fullName == "minecraft:redstone_wire") -> addThis()
                bs.fullName == "minecraft:redstone_torch" -> addThis()
                bs.fullName == "minecraft:redstone_lamp" -> addThis()
            }
            return@run
        }

        // Check below
        run {
            val bp = poweredBp.down()
            val bs = v.getBlock(bp).state
            fun addThis() = out.add(bp to bs)
            when {
                hardPower && (bs.fullName == "minecraft:redstone_wire") -> addThis()
                bs.fullName == "minecraft:redstone_lamp" -> addThis()
            }
            return@run
        }

        return out.toList()
    }


    // For each node, BFS forward and stop at components that aren't redstone wires
    for ((startNodePos, nodeData) in blockNodes) {
        val (startNodeBs, startNode) = nodeData

        //println("BFS start")

        //Add 1 to redstoneDist *only* when the emitting block is a redstone_wire
        data class BfsData(val pos: IVec3, val bs: BlockState, val redstoneDist: Int, ) {
            operator fun component0() = pos
        }

        val visited = hashSetOf<IVec3>()
        val q = ArrayDeque<BfsData>()

        // # Start BFS
        q.add(BfsData(startNodePos, startNodeBs, 0))
        while (q.isNotEmpty()) {
            val (currentNodePos, currentBs, currentRedstoneDist) = q.removeFirst()
            if (currentNodePos in visited) continue
            visited.add(currentNodePos)

            //println("BFS: ${currentNodePos}, ${currentBs} ")


            // ## BFS a different way depending on the block
            when (currentBs.fullName) {

                // # Handle torches
                "minecraft:redstone_torch", "minecraft:redstone_wall_torch" -> {

                    // Handle powering block above
                    val abovePos = currentNodePos.up()
                    val bsAbove = v.getBlock(abovePos).state
                    if (BsHelper.isConductive(bsAbove)) {
                        val nn = powerIntoBlockFindNextNodes(v, abovePos, hardPower = true)
                        for ((np, ns) in nn) {
                            if (ns.fullName != "minecraft:redstone_wire") {
                                val blockNode = blockNodes[np] ?: continue
                                blockNode.second.addInput(startNode, currentRedstoneDist)
                            } else {
                                // Continue BFS-ing if we found redstone
                                q.add(BfsData(np, ns, 0))
                            }
                        }
                    }

                    // Handle powering on the sides
                    for ((idx, d) in Direction.cardinal.withIndex()) {
                        val np = currentNodePos + d
                        val ns = v.getBlock(np).state

                        fun addCurrentAsNodeInput() {
                            val blockNode = blockNodes[np] ?: return
                            blockNode.second.addInput(startNode, 0)
                        }

                        when {
                            ns.looselyMatches(outgoingRepMatches[idx]) -> addCurrentAsNodeInput()
                            ns.looselyMatches(outgoingComparatorMatches[idx]) -> addCurrentAsNodeInput()
                            ns.fullName == "minecraft:redstone_lamp" -> addCurrentAsNodeInput()

                            ns.fullName == "minecraft:redstone_wire" -> q.add(BfsData(np, ns, 0))
                        }
                    }
                }

                // # Handle redstone wires
                "minecraft:redstone_wire" -> {

                    // Handle soft powering block below
                    val belowPos = currentNodePos.down()
                    val bsBelow = v.getBlock(belowPos).state
                    //println("bsBelow: ${bsBelow}")
                    if (BsHelper.isConductive(bsBelow)) {
                        val nn = powerIntoBlockFindNextNodes(v, belowPos, hardPower = false)
                        //println("ns")
                        for ((np, ns) in nn) {
                            //println(ns)
                            if (ns.fullName != "minecraft:redstone_wire") {
                                val blockNode = blockNodes[np] ?: continue
                                blockNode.second.addInput(startNode, currentRedstoneDist)
                            }
                        }
                        //println("nsend")
                    }

                    // Get the forced redirections. Redirects that are caused by blocks around
                    val forcedRedirects = hashMapOf<Direction, Boolean>()
                    for ((idx, d) in Direction.cardinal.withIndex()) {
                        forcedRedirects[d] = false
                        val np = currentNodePos + d
                        val ns = v.getBlock(np).state
                        // Get if the redstone is redirected by a universal redirector (redirects on every side) in this direction
                        val redirectedByUniversal = universalRedirectingBs.any { ns.looselyMatches(it) }
                        if (redirectedByUniversal) {
                            forcedRedirects[d] = true
                            continue
                        }
                        // More work needs to be done
                        // Check for directional redirectors around
                        when {
                            ns.looselyMatches(incomingRepMatches[idx]) -> forcedRedirects[d] = true
                            ns.looselyMatches(outgoingRepMatches[idx]) -> forcedRedirects[d] = true
                            ns.fullName == "minecraft:redstone_torch" -> forcedRedirects[d] = true
                            ns.fullName == "minecraft:redstone_wall_torch" -> forcedRedirects[d] = true
                            ns.looselyMatches(incomingObserverMatches[idx]) -> forcedRedirects[d] = true
                        }
                        // Check for redstone dust up a block
                        val bsAboveWire = v.getBlock(currentNodePos + Direction.UP).state
                        if (!BsHelper.isConductive(bsAboveWire)) {
                            val blockP1P1 = v.getBlock(np + Direction.UP).state
                            if (blockP1P1.fullName == "minecraft:redstone_wire") {
                                forcedRedirects[d] = true
                            }
                        }
                        // Check for redstone dust down a block
                        if (!BsHelper.isConductive(ns)) {
                            val blockP1M1 = v.getBlock(np + Direction.DOWN).state
                            if (blockP1M1.fullName == "minecraft:redstone_wire") {
                                forcedRedirects[d] = true
                            }
                        }
                    }
                    val trueRedirects = HashMap(forcedRedirects)
                    val forcedRedirsCount = forcedRedirects.count { (d, v) -> v }
                    if (forcedRedirsCount == 0) {
                        // If forced to point to no direction, its true redirection is determined by its
                        // block state, whether its a dot or not
                        val notDot = Direction.cardinal.any {
                            currentBs.getProp(it.propVal).orElse("none") != "none"
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

                    // For each redirect, send some powering through block bfs stuff
                    for (d in Direction.cardinal) {
                        val np = currentNodePos + d
                        val ns = v.getBlock(np).state
                        //println("ns::: $ns $np $startNodePos")
                        if (BsHelper.isConductive(ns)) {
                            // Soft powered so we don't do anything if we find redstone, cuz redstone
                            // doesn't power when an adjacent block gets soft powered
                            val nn = powerIntoBlockFindNextNodes(v, np, hardPower = false)
                            for ((np2, ns2) in nn) {
                                if (ns2.fullName != "minecraft:redstone_wire") {
                                    val blockNode = blockNodes[np2] ?: continue
                                    //println("should not happen either")
                                    blockNode.second.addInput(startNode, currentRedstoneDist)
                                }
                            }
                        }
                    }

                    // Power nodes around horizontally if any
                    for ((idx, d) in Direction.cardinal.withIndex()) {
                        val np = currentNodePos + d
                        val ns = v.getBlock(np).state

                        fun addCurrentAsNodeInput() {
                            val blockNode = blockNodes[np] ?: return
                            blockNode.second.addInput(startNode, 0)
                        }

                        /*fun addCurrentAsCompSideInput() {
                            val blockNode = blockNodes[np] ?: return
                            (blockNode.second as ComparatorNode).addSideInput(startNode, 0)
                        }*/

                        when {
                            ns.fullName == "minecraft:redstone_wire" -> q.add(BfsData(np, ns, currentRedstoneDist + 1))
                            ns.looselyMatches(outgoingRepMatches[idx]) -> addCurrentAsNodeInput()
                            ns.looselyMatches(outgoingComparatorMatches[idx]) -> addCurrentAsNodeInput()
                            ns.fullName == "minecraft:redstone_lamp" -> addCurrentAsNodeInput()
                            // TODO: Add side comparators
                        }
                    }

                    // Bfs redstone down or up
                    for ((idx, d) in Direction.cardinal.withIndex()) {
                        val np = currentNodePos + d
                        val ns = v.getBlock(np).state

                        // If block above is not conductive and there's one redstone diagonally up
                        if (!BsHelper.isConductive(v.getBlock(currentNodePos.up()).state)) {
                            if (v.getBlock((currentNodePos + d).up()).state.fullName == "minecraft:redstone_wire") {
                                q.add(BfsData(np, ns, currentRedstoneDist + 1))
                            }
                        }

                        // If block on the side is not conductive and theres one redstone diagonally down
                        if (BsHelper.isConductive(v.getBlock(currentNodePos + d).state)) {
                            if (v.getBlock((currentNodePos + d).up()).state.fullName == "minecraft:redstone_wire") {
                                // If block below main redstone is conductive
                                if (BsHelper.isConductive(v.getBlock(currentNodePos.down()).state)) {
                                    q.add(BfsData(np, ns, currentRedstoneDist + 1))
                                }
                            }
                        }
                    }
                }
            }
        }
    }



    val graph = RsGraph()
    for ((nodePos, nodeData) in blockNodes) {
        val (bs, node) = nodeData
        graph.addNode(node)
    }

    val nodePositions = HashMap<RsNode, IVec3>()
    for ((pos, data) in blockNodes) nodePositions[data.second] = pos

    return Pair(graph, nodePositions)


    // OLD PLAN:
    // Identify redstone wire clumps

    // Identify IO nodes of redstone wire clumps

    // BFS down each clump from each input, and add itself into each output it reaches

    // For each node check which node they "directly" have as input and add them if they do

}
*/









