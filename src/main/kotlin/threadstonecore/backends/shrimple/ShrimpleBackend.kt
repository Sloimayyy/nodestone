package com.sloimay.threadstonecore.backends.shrimple

import com.sloimay.threadstonecore.backends.RedstoneSimBackend
import com.sloimay.threadstonecore.backends.mamba.graph.nodes.MambaNodeType
import com.sloimay.threadstonecore.backends.shrimple.graph.ShrimpleGraph
import com.sloimay.threadstonecore.backends.shrimple.graph.nodes.*
import com.sloimay.threadstonecore.backends.shrimple.graph.nodes.ShrimpleNodeIntRepr.Companion.getNextNodeIntWithDynDataBits
import com.sloimay.threadstonecore.backends.shrimple.helpers.ShrimpleHelper
import com.sloimay.threadstonecore.backends.shrimple.helpers.ShrimpleHelper.Companion.getBitField
import com.sloimay.threadstonecore.backends.shrimple.helpers.ShrimpleHelper.Companion.setBitField
import com.sloimay.threadstonecore.backends.shrimple.helpers.int
import com.sloimay.threadstonecore.helpers.ThscUtils.Companion.toBitString
import com.sloimay.threadstonecore.redstoneir.RedstoneBuildIR
import com.sloimay.threadstonecore.redstoneir.from.fromVolume
import me.sloimay.mcvolume.IntBoundary
import me.sloimay.mcvolume.McVolume
import me.sloimay.mcvolume.block.BlockState
import me.sloimay.smath.vectors.IVec3
import kotlin.math.max


private abstract class ShrimpleScheduledAction
private data class ShrimpleScheduledUserInput(val inputNode: ShrimpleUserInputNode, val powerToSet: Int) : ShrimpleScheduledAction()

private class DynIntArray(capacity: Int) {
    val arr = IntArray(capacity)
    var count = 0

    fun add(i: Int) {
        arr[count] = i
        count += 1
    }

    fun clear() {
        count = 0
    }
}



class ShrimpleBackend private constructor(
    volume: McVolume,
    simBounds: IntBoundary,


    val graph: ShrimpleGraph,
    val graphBuffer: IntArray,

    val edgePointerArray: IntArray,
    val edgeArray: IntArray,

    val positionedNodes: HashMap<IVec3, ShrimpleNode>,
    val positionedUserInputNodes: HashMap<IVec3, ShrimpleUserInputNode>,

) : RedstoneSimBackend(volume, simBounds) {

    var currentTick: Long = 0
        private set

    private val nodeUpdatesDynArrays = listOf(
        DynIntArray(graph.nodes.size),
        DynIntArray(graph.nodes.size),
    )
    private val nodesAddedBitmap = LongArray((graph.nodes.size + 63) / 64) { 0 }


    private val nodeDataLastVisualUpdate = ByteArray(graph.nodes.size)
    private var nodeDataLastVisualUpdateTick = currentTick
    private val nodeChangeArray = BooleanArray(nodeDataLastVisualUpdate.size) { true }

    private val timestampArray = LongArray(graph.nodes.size * 2) { currentTick }


    // Timestamp -> IoScheduleEntry
    private val userInputScheduler: HashMap<Long, MutableList<ShrimpleScheduledUserInput>> = hashMapOf()



    init {
        // Add every node to be ticked on the next tick
        val currDynArr = this.getCurrNodeUpdatesDynArray()
        for (node in graph.nodes) {
            currDynArr.add(node.idxInArray!!)
        }

    }


    companion object {

        fun new(vol: McVolume, simVolBounds: IntBoundary): ShrimpleBackend {

            val irGraph = RedstoneBuildIR.fromVolume(vol)
            val graphRes = ShrimpleGraph.fromIR(irGraph)
            val graph = graphRes.graph
            val positionedNodes = graphRes.positionedNodes
            val positionedUserInputNodes = graphRes.positionedUserInputNodes

            val serResult = graph.serialize()
            val graphBuffer = serResult.graphArray
            val edgePointerArray = serResult.edgePointerArray
            val edgeArray = serResult.edgeArray

            for (i in graphBuffer.indices) {
                println(toBitString(graphBuffer[i]))
            }
            println("==================")
            for (i in edgePointerArray.indices) {
                println(toBitString(edgePointerArray[i]))
            }
            println("==================")
            for (i in edgeArray.indices) {
                println(toBitString(edgeArray[i]))
            }

            return ShrimpleBackend(
                vol,
                simVolBounds,

                graph,
                graphBuffer,

                edgePointerArray,
                edgeArray,

                positionedNodes,
                positionedUserInputNodes
            )
        }

    }

    override fun updateRepr(
        updateVolume: Boolean,
        onlyNecessaryVisualUpdates: Boolean,
        renderCallback: (renderPos: IVec3, newBlockState: BlockState) -> Unit
    ) {

        if (onlyNecessaryVisualUpdates) {
            for (nodeIdx in this.graph.nodes.indices) {
                val lastNodeData = this.nodeDataLastVisualUpdate[nodeIdx]
                val currentNodeInt = this.graphBuffer[nodeIdx]
                val nodeDataCurrent = ShrimpleNodeIntRepr.getIntCurrentDataBits(currentNodeInt).toByte()
                /*
                TODO: Can be improved. For example, comparators changing their output SS from 15 to 14 don't need
                      a visual update. Same for repeaters that are staying on but have their scheduler changing.
                 */
                nodeChangeArray[nodeIdx] = lastNodeData != nodeDataCurrent
                nodeDataLastVisualUpdate[nodeIdx] = nodeDataCurrent
            }
            this.nodeDataLastVisualUpdateTick = currentTick
        } else {
            // Trick the code to update every node (bad)
            nodeChangeArray.fill(true)
        }


        val ioOnly = false
        for (nodeIdx in this.graph.nodes.indices) {
            // Check if we actually need to visually update the node
            if (nodeChangeArray[nodeIdx] == false) continue

            val nodeInt = graphBuffer[nodeIdx]
            val nodeType = ShrimpleNodeIntRepr.getIntCurrentType(nodeInt)

            // is io check
            val isIoNode = nodeType == ShrimpleNodeType.USER_INPUT.int || nodeType == MambaNodeType.LAMP.int
            if (ioOnly && !isIoNode) continue

            // get pos and don't visually update if it doesn't have a position
            val position = this.graph.nodes[nodeIdx].pos ?: continue


            val bs = volume.getBlock(position).state
            val bsMut = bs.toMutable()
            val nodeData = ShrimpleNodeIntRepr.getIntCurrentDataBits(nodeInt)

            // Modify bs
            when (nodeType) {
                ShrimpleNodeType.COMPARATOR.int -> {
                    val outputSs = (nodeData and 0xF)
                    bsMut.setProp("powered", if (outputSs > 0) "true" else "false")
                }
                ShrimpleNodeType.USER_INPUT.int -> {
                    when (bs.fullName) {
                        "minecraft:lever",
                        "minecraft:stone_button",
                        "minecraft:stone_pressure_plate" -> {
                            val outputSs = (nodeData and 0xF)
                            bsMut.setProp("powered", if (outputSs > 0) "true" else "false")
                        }
                        else -> {}
                    }
                }
                ShrimpleNodeType.REPEATER.int -> {
                    // Output bool is the first bit of the scheduler
                    val poweredBit = nodeData and 0x1
                    bsMut.setProp("powered", if (poweredBit == 1) "true" else "false")
                }
                ShrimpleNodeType.TORCH.int, MambaNodeType.LAMP.int -> {
                    val poweredBit = nodeData and 0x1
                    bsMut.setProp("lit", if (poweredBit == 1) "true" else "false")
                }
                else -> {

                }
            }

            val newBs = bsMut.toImmutable()
            if (updateVolume) {
                /* TODO: Very very bad performance */
                val newVolB = volume.getPaletteBlock(newBs)
                volume.setBlock(position, newVolB)
            }
            renderCallback(position, newBs)
        }

    }

    override fun getInputNodePositions(): Set<IVec3> {
        return positionedUserInputNodes.keys
    }

    override fun scheduleButtonPress(ticksFromNow: Int, pressLength: Int, inputNodePos: IVec3) {
        this.scheduleUserInputChange(ticksFromNow, inputNodePos, 15)
        this.scheduleUserInputChange(ticksFromNow + pressLength, inputNodePos, 0)
    }

    override fun scheduleUserInputChange(ticksFromNow: Int, inputNodePos: IVec3, power: Int) {
        val inputNode = this.positionedUserInputNodes[inputNodePos]
            ?: throw Exception("Inputted node position $inputNodePos is not an input node.")

        val tickTimestamp = currentTick + ticksFromNow
        userInputScheduler.putIfAbsent(tickTimestamp, mutableListOf())
        val inputChangesThisTick = userInputScheduler[tickTimestamp]!!
        inputChangesThisTick.add(ShrimpleScheduledUserInput(inputNode, power))
    }


    private fun handleUserInputs() {

        // Remove schedules that may be outdated but there shouldn't be any
        val outdatedTicks = mutableListOf<Long>()
        for ((tick, actions) in this.userInputScheduler) {
            if (tick < currentTick) {
                outdatedTicks.add(tick)
            }
        }
        outdatedTicks.forEach { this.userInputScheduler.remove(it) }
        val actionsThisTick = this.userInputScheduler.remove(currentTick) ?: return

        // Update every input node
        for (action in actionsThisTick) {
            val power = action.powerToSet
            val node = action.inputNode

            val nodeInt = graphBuffer[node.idxInArray!!]

            val nodeIntRepr = ShrimpleNodeIntRepr.fromInt(nodeInt)
            val newDynamicData = ShrimpleUserInputNode.serializeDataBits(power)
            nodeIntRepr.evenTicksData = newDynamicData
            nodeIntRepr.oddTicksData = newDynamicData

            val newNodeInt = nodeIntRepr.toInt()
            graphBuffer[node.idxInArray!!] = newNodeInt
        }
    }

    private fun getCurrNodeUpdatesDynArray(): DynIntArray {
        return nodeUpdatesDynArrays[(currentTick and 1).toInt()]
    }

    private fun getNextNodeUpdatesDynArray(): DynIntArray {
        return nodeUpdatesDynArrays[((currentTick+1) and 1).toInt()]
    }






    override fun tickWhile(pred: () -> Boolean) {

        val graphBuf = this.graphBuffer
        //val edgePointerArray = this.edgePointerArray
        //val edgeArray = this.edgeArray

        while (pred()) {

            println("===== GRAPH BEFORE TICK")
            for (i in graphBuffer.indices) {
                println(toBitString(graphBuffer[i]))
            }

            // Clear nodes added bitmap
            clearNodeUpdateBitmap()

            val currUpdateDynArray = this.getCurrNodeUpdatesDynArray()
            val nextUpdateDynArray = this.getNextNodeUpdatesDynArray()

            println("== Dyn array:")
            for (i in 0 until currUpdateDynArray.count) {
                println(toBitString(currUpdateDynArray.arr[i]))
            }

            // # Update every node in the update dyn array
            for (nodeUpdateIdx in 0 until currUpdateDynArray.count) {
                val nodeIdx = currUpdateDynArray.arr[nodeUpdateIdx]

                println("TICKING NODE: ${nodeIdx}")

                val nodeInt = graphBuf[nodeIdx]

                val nodeType = ShrimpleNodeIntRepr.getIntCurrentType(nodeInt)
                val nodeDynData = ShrimpleNodeIntRepr.getIntCurrentDataBits(nodeInt)
                val edgePointerDataBaseIdx = nodeIdx * 3

                var updateOutputs = false

                var nextNodeDynData = nodeDynData

                when (nodeType) {
                    ShrimpleNodeType.TORCH.int -> {
                        val litBit = ShrimpleTorchNode.getDynDataLit(nodeDynData)

                        val powers = getNodeInputPowers(nodeIdx)
                        val backPower = powers and 0xF
                        val sidePower = (powers ushr 4) and 0xF

                        val newLit = (backPower == 0).int
                        nextNodeDynData = setBitField(nextNodeDynData, newLit, 0, 1)

                        graphBuf[nodeIdx] = getNextNodeIntWithDynDataBits(nodeInt, nextNodeDynData)
                        // REMEMBER to switch the parity somewhere here

                        // TESTING
                        updateOutputs = true
                    }
                }


                // Update outputs
                if (updateOutputs == true) {
                    val outputEdgeStart = edgePointerArray[edgePointerDataBaseIdx + 1]
                    val outputEdgeCount = (edgePointerArray[edgePointerDataBaseIdx + 2] ushr 16) and 0xFFFF

                    for (i in 0 until outputEdgeCount) {
                        val nodeIdxToUpdatePointer = edgeArray[outputEdgeStart + i]
                        val nodeIdxToUpdate = nodeIdxToUpdatePointer ushr 5
                        // TODO: do something with the distance later
                        val pointerDist = nodeIdxToUpdatePointer and 0xF

                        println("= Thinking of adding node: ${nodeIdxToUpdate}")

                        if (!wasNodeUpdateAdded(nodeIdxToUpdate)) {
                            setNodeUpdateAdded(nodeIdxToUpdate)
                            println("= Node: ${nodeIdxToUpdate} was added!")
                            nextUpdateDynArray.add(nodeIdxToUpdate)
                        } else {
                            println("= Node: ${nodeIdxToUpdate} was *not* added'}")
                        }
                    }
                }
            }

            println("===== GRAPH AFTER TICK")
            for (i in graphBuffer.indices) {
                println(toBitString(graphBuffer[i]))
            }

            // End of tick
            currUpdateDynArray.clear()
            currentTick += 1
        }

    }

    private fun getNodeInputPowers(nodeIdx: Int): Int {
        var backPower = 0
        var sidePower = 0

        val edgePointerDataBaseIdx = nodeIdx * 3
        val inputEdgeStart = edgePointerArray[edgePointerDataBaseIdx + 1]
        val inputEdgeCount = (edgePointerArray[edgePointerDataBaseIdx + 2] ushr 0) and 0xFFFF

        for (i in 0 until inputEdgeCount) {
            val inputNodeIdxPointer = edgeArray[inputEdgeStart + i]
            val inputNodeIdx = inputNodeIdxPointer ushr 5
            val dist = inputNodeIdxPointer and 0xF
            val sideBit = (inputNodeIdxPointer ushr 4) and 0x1

            val inputNodeInt = graphBuffer[inputNodeIdx]
            val mostRecentDynBits = ShrimpleNodeIntRepr.getIntCurrentDataBits(inputNodeInt)

            val nodeType = ShrimpleNodeIntRepr.getIntCurrentType(inputNodeInt)
            val power = getNodePower(nodeType, mostRecentDynBits)

            val depletedPower = max(0, power - dist)
            if (sideBit == 0) {
                backPower = max(backPower, depletedPower)
            } else {
                sidePower = max(sidePower, depletedPower)
            }
        }

        return (backPower and 0xF) or ((sidePower and 0xF) shl 4)
    }

    private fun getNodePower(nodeType: Int, dynBits: Int): Int {
        return when (nodeType) {

            ShrimpleNodeType.TORCH.int -> {
                val lit = getBitField(dynBits, 0, 1)
                lit * 15
            }

            else -> {
                throw Exception("UNREACHABLE")
            }
        }
    }

    private fun setNodeUpdateAdded(nodeIdx: Int) {
        val idxInArray = nodeIdx ushr 6
        val idxInLong = nodeIdx and 0x3F
        this.nodesAddedBitmap[idxInArray] = this.nodesAddedBitmap[idxInArray] or (1L shl idxInLong)
    }

    private fun wasNodeUpdateAdded(nodeIdx: Int): Boolean {
        val idxInArray = nodeIdx ushr 6
        val idxInLong = nodeIdx and 0x3F
        val v = this.nodesAddedBitmap[idxInArray]
        return ((v ushr idxInLong) and 1) == 1L
    }

    private fun clearNodeUpdateBitmap() {
        this.nodesAddedBitmap.fill(0)
    }


}