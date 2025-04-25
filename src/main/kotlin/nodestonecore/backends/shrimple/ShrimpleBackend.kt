package com.sloimay.nodestonecore.backends.shrimple

import com.sloimay.mcvolume.McVolume
import com.sloimay.mcvolume.block.BlockState
import com.sloimay.smath.vectors.IVec3
import com.sloimay.nodestonecore.backends.RedstoneSimBackend
import com.sloimay.nodestonecore.backends.RedstoneSimInput
import com.sloimay.nodestonecore.backends.shrimple.graph.ShrimpleGraph
import com.sloimay.nodestonecore.backends.shrimple.graph.nodes.*
import com.sloimay.nodestonecore.backends.shrimple.graph.nodes.ShrimpleNodeIntRepr.Companion.getNextNodeIntWithDynDataBits
import com.sloimay.nodestonecore.backends.shrimple.helpers.ShrimpleHelper.Companion.getBitField
import com.sloimay.nodestonecore.backends.shrimple.helpers.int
import com.sloimay.nodestonecore.redstoneir.RedstoneBuildIR
import com.sloimay.nodestonecore.redstoneir.from.fromVolume
import com.sloimay.nodestonecore.redstoneir.helpers.*
import com.sloimay.smath.clamp
import com.sloimay.smath.geometry.boundary.IntBoundary
import kotlin.math.max



internal class ShrimpleRenderRsWireInput(val node: ShrimpleNode, val dist: Int,)
internal class ShrimpleRenderRsWire(val inputs: MutableList<ShrimpleRenderRsWireInput>, var lastSs: Int,)



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

    internal val graph: ShrimpleGraph,
    val graphBuffer: IntArray,

    val edgePointerArray: IntArray,
    val edgeArray: IntArray,

    val positionedNodes: HashMap<IVec3, ShrimpleNode>,
    val positionedUserInputNodes: HashMap<IVec3, ShrimpleUserInputNode>,

    internal val renderedRsWires: List<Pair<IVec3, ShrimpleRenderRsWire>>,

    val redstoneSimInputs: List<RedstoneSimInput>,

    // == Compile flags
    val ioOnly: Boolean,
    val noWireRendering: Boolean,
    // ==

) : RedstoneSimBackend(volume, simBounds) {

    var currentRsTick: Long = 0
        private set
    private var gtParity = true


    private val prioritisedDynArrays = listOf(
        arrayOf(
            DynIntArray(graph.nodes.size),
            DynIntArray(graph.nodes.size),
            DynIntArray(graph.nodes.size),
            DynIntArray(graph.nodes.size),
        ),
        arrayOf(
            DynIntArray(graph.nodes.size),
            DynIntArray(graph.nodes.size),
            DynIntArray(graph.nodes.size),
            DynIntArray(graph.nodes.size),
        )
    )
    private val nodesAddedBitmap = LongArray((graph.nodes.size + 63) / 64) { 0 }


    private val nodeDataLastVisualUpdate = ByteArray(graph.nodes.size)
    private var nodeDataLastVisualUpdateTick = currentRsTick
    private val nodeChangeArray = BooleanArray(nodeDataLastVisualUpdate.size) { true }

    private val timestampArray = LongArray(graph.nodes.size) { currentRsTick }


    // Timestamp -> IoScheduleEntry
    // TODO: Not thread safe (same problems for the other backends)
    private val userInputScheduler: HashMap<Long, MutableList<ShrimpleScheduledUserInput>> = hashMapOf()


    init {
        // Add every node to be ticked on the next tick
        val currDynArrays = this.getCurrNodeUpdatesDynArrays()
        for (node in graph.nodes) {
            val channel = currDynArrays[node.updatePriority]
            channel.add(node.idxInArray!!)
        }

    }


    companion object {

        fun new(vol: McVolume, simVolBounds: IntBoundary, compileFlags: List<String>): ShrimpleBackend {

            val irGraph = RedstoneBuildIR.fromVolume(vol)
            val graphRes = ShrimpleGraph.fromIR(irGraph)
            val graph = graphRes.graph
            val positionedNodes = graphRes.positionedNodes
            val positionedUserInputNodes = graphRes.positionedUserInputNodes
            val renderedRsWires = graphRes.rsWires

            val serResult = graph.serialize()
            val graphBuffer = serResult.graphArray
            val edgePointerArray = serResult.edgePointerArray
            val edgeArray = serResult.edgeArray

            val redstoneSimInputs = positionedUserInputNodes.map { ShrimpleInput(it.key) }

            /*for (i in graphBuffer.indices) {
                println("${toBitString(graphBuffer[i])} - id: $i - pos: ${graph.nodes[i].pos ?: "no position"}")
            }
            println("==================")
            for (i in edgePointerArray.indices) {
                println(toBitString(edgePointerArray[i]))
            }
            println("==================")
            for (i in edgeArray.indices) {
                println(toBitString(edgeArray[i]))
            }*/

            // Process compile flags:
            val ioOnly = "io-only" in compileFlags
            val noWireRendering = "no-wire-rendering" in compileFlags


            return ShrimpleBackend(
                vol,
                simVolBounds,

                graph,
                graphBuffer,

                edgePointerArray,
                edgeArray,

                positionedNodes,
                positionedUserInputNodes,

                renderedRsWires,

                redstoneSimInputs,

                // Compile flags
                ioOnly,
                noWireRendering,
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
                //println("======= Checking node: '${nodeIdx}', for visual updates needed")
                val lastNodeData = this.nodeDataLastVisualUpdate[nodeIdx]
                //println("            Last time node data: ${lastNodeData}")
                val currentNodeInt = this.graphBuffer[nodeIdx]
                val nodeDataCurrent = ShrimpleNodeIntRepr.getIntParityPointedDataBits(currentNodeInt).toByte()
                //println("            current pooled node data: ${nodeDataCurrent}")
                /*
                TODO: Can be improved. For example, comparators changing their output SS from 15 to 14 don't need
                      a visual update. Same for repeaters that are staying on but have their scheduler changing.
                 */
                nodeChangeArray[nodeIdx] = lastNodeData != nodeDataCurrent
                nodeDataLastVisualUpdate[nodeIdx] = nodeDataCurrent
            }
            this.nodeDataLastVisualUpdateTick = currentRsTick
        } else {
            // Trick the code to update every node (bad)
            nodeChangeArray.fill(true)
        }


        for (nodeIdx in this.graph.nodes.indices) {
            // Check if we actually need to visually update the node
            if (nodeChangeArray[nodeIdx] == false) continue

            val nodeInt = graphBuffer[nodeIdx]
            val nodeType = ShrimpleNodeIntRepr.getIntCurrentType(nodeInt)

            // is io check
            val isIoNode = nodeType == ShrimpleNodeType.USER_INPUT.int || nodeType == ShrimpleNodeType.LAMP.int
            if (ioOnly && !isIoNode) continue

            // get pos and don't visually update if it doesn't have a position
            val position = this.graph.nodes[nodeIdx].pos ?: continue


            val bs = volume.getBlockState(position)
            // TODO: the immut -> mut transformation is kind of terrible for performance XD
            val bsMut = bs.toMutable()
            val nodeDynData = ShrimpleNodeIntRepr.getIntParityPointedDataBits(nodeInt)

            // Modify bs
            when (nodeType) {
                ShrimpleNodeType.COMPARATOR.int -> {
                    val outputSs = (nodeDynData and 0xF)
                    bsMut.setProp("powered", if (outputSs > 0) "true" else "false")
                }
                ShrimpleNodeType.USER_INPUT.int -> {
                    when  {
                        // Strictly one / off user inputs
                        bs.fullName == "minecraft:lever" ||
                        (bs.isPressurePlate() && !bs.isWeightedPressurePlate()) ||
                        bs.isWoodenButton() ||
                        bs.isStoneButton() -> {
                            val outputSs = (nodeDynData and 0xF)
                            bsMut.setProp("powered", if (outputSs > 0) "true" else "false")
                        }
                        // Weighted pressure plates
                        bs.isWeightedPressurePlate() -> {
                            val outputSs = (nodeDynData and 0xF)
                            bsMut.setProp("power", outputSs.toString())
                        }
                        else -> {}
                    }
                }
                ShrimpleNodeType.REPEATER.int -> {
                    // Output bool is the first bit of the scheduler
                    val poweredBit = ShrimpleRepeaterNode.getDynDataScheduler(nodeDynData) and 0x1
                    val lockedBit = ShrimpleRepeaterNode.getDynDataLocked(nodeDynData)
                    bsMut.setProp("powered", if (poweredBit == 1) "true" else "false")
                    bsMut.setProp("locked", if (lockedBit == 1) "true" else "false")
                }
                ShrimpleNodeType.TORCH.int, ShrimpleNodeType.LAMP.int -> {
                    val poweredBit = nodeDynData and 0x1
                    bsMut.setProp("lit", if (poweredBit == 1) "true" else "false")
                }
                else -> {

                }
            }

            val newBs = bsMut.toImmutable()
            if (updateVolume) {
                /* TODO: actually not so bad perf but maybe look into better alternatives */
                volume.setBlockState(position, newBs)
            }
            renderCallback(position, newBs)
        }

        if (!noWireRendering && !ioOnly) {
            // Get the SS this redstone wire should be
            for ((rsWirePos, rsWire) in renderedRsWires) {
                var totalSs = 0
                for (input in rsWire.inputs) {
                    val nodeIdxInArr = input.node.idxInArray!!
                    val nodeDist = input.dist
                    val nodeInt = graphBuffer[nodeIdxInArr]
                    val nodeType = ShrimpleNodeIntRepr.getIntCurrentType(nodeInt)
                    val nodeDynBits = ShrimpleNodeIntRepr.getIntParityPointedDataBits(nodeInt)
                    val powerSs = (getNodePower(nodeType, nodeDynBits) - nodeDist).clamp(0, 15)
                    totalSs = max(powerSs, totalSs)
                }

                // Don't place a block back if the ss didn't change
                if ((totalSs == rsWire.lastSs) && onlyNecessaryVisualUpdates) continue
                rsWire.lastSs = totalSs

                val bs = volume.getBlockState(rsWirePos)
                // TODO: same problem as node rendering. immut -> mut conversion is bad lol
                val bsMut = bs.toMutable()
                bsMut.setProp("power", totalSs.toString())
                val newBs = bsMut.toImmutable()

                if (updateVolume) {
                    /* TODO: actually not so bad perf but maybe look into better alternatives */
                    volume.setBlockState(rsWirePos, newBs)
                }
                renderCallback(rsWirePos, newBs)
            }
        }
    }

    override fun getInputs(): List<RedstoneSimInput> = redstoneSimInputs

    override fun scheduleButtonPress(ticksFromNow: Int, pressLength: Int, input: RedstoneSimInput) {
        input as ShrimpleInput
        this.scheduleUserInputChange(ticksFromNow, input, 15)
        this.scheduleUserInputChange(ticksFromNow + pressLength, input, 0)
    }

    override fun scheduleUserInputChange(ticksFromNow: Int, input: RedstoneSimInput, power: Int) {
        input as ShrimpleInput
        val inputNode = this.positionedUserInputNodes[input.pos]
            ?: throw Exception("Inputted node position ${input.pos} is not an input node.")

        val tickTimestamp = currentRsTick + ticksFromNow
        userInputScheduler.putIfAbsent(tickTimestamp, mutableListOf())
        val inputChangesThisTick = userInputScheduler[tickTimestamp]!!
        inputChangesThisTick.add(ShrimpleScheduledUserInput(inputNode, power))
    }


    private val handleUserInputs_outdatedTicks = mutableListOf<Long>()
    private fun handleUserInputs(nextUpdateDynArrays: Array<DynIntArray>) {

        // Remove schedules that may be outdated but there shouldn't be any

        /*for ((tick, actions) in this.userInputScheduler) {
            if (tick < currentTick) {
                handleUserInputs_outdatedTicks.add(tick)
            }
        }*/
        //handleUserInputs_outdatedTicks.forEach { this.userInputScheduler.remove(it) }
        //handleUserInputs_outdatedTicks.clear()

        if (this.userInputScheduler.size == 0) return

        val actionsThisTick = this.userInputScheduler.remove(currentRsTick) ?: return

        // Update every input node
        for (action in actionsThisTick) {
            val power = action.powerToSet
            val node = action.inputNode

            val nodeIdx = node.idxInArray!!
            val nodeInt = graphBuffer[nodeIdx]

            // Get the new int representation (basically just write the SS into the node)
            val nodeIntRepr = ShrimpleNodeIntRepr.fromInt(nodeInt)
            val newDynamicData = ShrimpleUserInputNode.serializeDataBits(power)
            nodeIntRepr.evenTicksData = newDynamicData
            nodeIntRepr.oddTicksData = newDynamicData

            // Send an update to the outputs of the node input nodes otherwise
            // they won't realise the input SS has changed
            this.sendUpdatesToNodeOutputs(nodeIdx, nextUpdateDynArrays)

            val newNodeInt = nodeIntRepr.toInt()
            graphBuffer[nodeIdx] = newNodeInt
        }
    }

    private fun getCurrNodeUpdatesDynArrays(): Array<DynIntArray> {
        return prioritisedDynArrays[(currentRsTick and 1).toInt()]
    }

    private fun getNextNodeUpdatesDynArrays(): Array<DynIntArray> {
        return prioritisedDynArrays[((currentRsTick+1) and 1).toInt()]
    }






    override fun tickWhile(pred: () -> Boolean) {

        val graphBuf = this.graphBuffer
        //val edgePointerArray = this.edgePointerArray
        //val edgeArray = this.edgeArray

        while (pred()) {
            // This backend works with redstone ticks only, MCHPRS-style
            // so we skip every other gt
            gtParity = !gtParity
            if (gtParity) continue

            /*println("===== GRAPH BEFORE TICK")
            for (i in graphBuffer.indices) {
                println(toBitString(graphBuffer[i]))
            }*/

            // Clear nodes added bitmap
            clearNodeUpdateBitmap()

            val currUpdateDynArrays = this.getCurrNodeUpdatesDynArrays()
            val nextUpdateDynArrays = this.getNextNodeUpdatesDynArrays()

            /*println("== Dyn array:")
            for (i in 0 until currUpdateDynArray.count) {
                println(toBitString(currUpdateDynArray.arr[i]))
            }*/

            //var isTickBeningnig = true

            // # Update every node in the update dyn array
            for (dynArr in currUpdateDynArrays) for (nodeUpdateIdx in 0 until dynArr.count) {

                /*if (isTickBeningnig) {
                    println("===== TICK BEGINGINGING")
                }
                isTickBeningnig = false*/

                val nodeIdx = dynArr.arr[nodeUpdateIdx]

                //println("TICKING NODE: ${nodeIdx}")

                val nodeInt = graphBuf[nodeIdx]

                val nodeType = ShrimpleNodeIntRepr.getIntCurrentType(nodeInt)
                // Guaranteed to not have been updated yet, so the most recent dyn data
                // is the one pointed to
                val nodeDynData = ShrimpleNodeIntRepr.getIntParityPointedDataBits(nodeInt)
                val nodeConstData = ShrimpleNodeIntRepr.getIntConstantData(nodeInt)
                val nodeUpdatePriority = ShrimpleNodeIntRepr.getIntPriority(nodeInt)

                var updateOutputs = false

                var nextNodeDynData = nodeDynData

                when (nodeType) {
                    ShrimpleNodeType.TORCH.int -> {
                        val oldLit = ShrimpleTorchNode.getDynDataLit(nodeDynData)

                        val powers = getNodeInputPowers(nodeIdx, nodeUpdatePriority)
                        val backPower = powers and 0xF
                        val sidePower = (powers ushr 4) and 0xF

                        val newLit = (backPower == 0).int
                        nextNodeDynData = ShrimpleTorchNode.setDynDataLit(nextNodeDynData, newLit)

                        graphBuf[nodeIdx] = getNextNodeIntWithDynDataBits(nodeInt, nextNodeDynData)
                        timestampArray[nodeIdx] = currentRsTick

                        updateOutputs = newLit != oldLit
                    }

                    ShrimpleNodeType.LAMP.int -> {
                        val oldLit = ShrimpleLampNode.getDynDataLit(nodeDynData)

                        val powers = getNodeInputPowers(nodeIdx, nodeUpdatePriority)
                        val backPower = powers and 0xF
                        val sidePower = (powers ushr 4) and 0xF

                        val newLit = (backPower > 0).int
                        nextNodeDynData = ShrimpleLampNode.setDynDataLit(nextNodeDynData, newLit)

                        graphBuf[nodeIdx] = getNextNodeIntWithDynDataBits(nodeInt, nextNodeDynData)
                        timestampArray[nodeIdx] = currentRsTick
                    }

                    ShrimpleNodeType.REPEATER.int -> {

                        // Base implementation from Mamba
                        val oldScheduler = ShrimpleRepeaterNode.getDynDataScheduler(nodeDynData)
                        val oldLocked = ShrimpleRepeaterNode.getDynDataLocked(nodeDynData)
                        val oldUpdateTimer = ShrimpleRepeaterNode.getDynDataUpdateTimer(nodeDynData)

                        val delay = ShrimpleRepeaterNode.getConstDataDelay(nodeConstData)

                        val oldOutPower = ShrimpleRepeaterNode.getPower(nodeDynData)

                        val powers = getNodeInputPowers(nodeIdx, nodeUpdatePriority)
                        val backPower = powers and 0xF
                        val sidePower = (powers ushr 4) and 0xF

                        val newLocked = (sidePower > 0).int
                        var newScheduler = oldScheduler


                        /*if (newLocked != oldLocked) {
                            println("newlocked $newLocked | oldlocked $oldLocked " +
                                    "| power back $backPower | power side $sidePower")
                        }*/


                        val repOutput = oldScheduler and 0x1
                        val schedMask = (1 shl (delay+1)) - 1
                        if (newLocked == 0) {
                            val input = (backPower > 0).int
                            if (input == 1 && repOutput == 1) {
                                newScheduler = schedMask // pulse extension
                            } else {
                                // # LFSR magic
                                val schedFirstBit = (newScheduler ushr delay) and 0x1
                                newScheduler = ((newScheduler and schedMask) ushr 1)
                                newScheduler = newScheduler or ((input or (repOutput.inv() and schedFirstBit)) shl delay)
                            }
                        } else {
                            newScheduler = schedMask * repOutput
                        }

                        // Decrement update timer if on
                        val newUpdateTimer = if (oldOutPower == 0) {
                            delay
                        } else {
                            // Is on
                            max( oldUpdateTimer - 1, 0 )
                        }

                        // Populating the next dyn data
                        nextNodeDynData = ShrimpleRepeaterNode.setDynDataLocked(nextNodeDynData, newLocked)
                        nextNodeDynData = ShrimpleRepeaterNode.setDynDataUpdateTimer(nextNodeDynData, newUpdateTimer)
                        nextNodeDynData = ShrimpleRepeaterNode.setDynDataScheduler(nextNodeDynData, newScheduler)

                        // The back doesn't change so the scheduler only shifts in zeros, which we can
                        // emulate by not updating ourselves
                        // We can also not update ourselves if the sched is all 1s, as the only way
                        // for the repeater to shift in a 0 would be if the signal behind it changes,
                        // which is meant to be impossible without an update being sent its way
                        // If there are repeater bugs, they probably come from here though LOL
                        // This section also handles the final part of the update timer thing.
                        // So double the trouble lmaooo hopefully it just works
                        val newSchedAll0s = newScheduler == 0
                        val newSchedAll1s = newScheduler == schedMask
                        val newOutPower = ShrimpleRepeaterNode.getPower(nextNodeDynData)
                        //println("old update timer ${oldUpdateTimer}   | new update timer ${newUpdateTimer}   " +
                        //        "| old out power ${oldOutPower}   | new out power ${newOutPower}")
                        if ((newOutPower > 0 && oldUpdateTimer > 0) || (!newSchedAll0s && !newSchedAll1s)) {
                            //println("self updated mr repeater (update timer branch)")
                            val channel = nextUpdateDynArrays[nodeUpdatePriority]
                            addNodeToBitmapAndDynArrayIfNotAlready(nodeIdx, channel)
                        }


                        // Final write back
                        graphBuf[nodeIdx] = getNextNodeIntWithDynDataBits(nodeInt, nextNodeDynData)
                        timestampArray[nodeIdx] = currentRsTick

                        // Updates
                        val outputChanged = newOutPower != oldOutPower
                        updateOutputs = outputChanged
                    }

                    ShrimpleNodeType.COMPARATOR.int -> {

                        val hasFarInput = ShrimpleComparatorNode.getConstDataHasFarInput(nodeConstData)
                        val farInputSs = ShrimpleComparatorNode.getConstDataFarInputSs(nodeConstData)
                        val mode = ShrimpleComparatorNode.getConstDataMode(nodeConstData)

                        val oldOutputSs = ShrimpleComparatorNode.getDynDataOutputSs(nodeDynData)

                        val powers = getNodeInputPowers(nodeIdx, nodeUpdatePriority)
                        var backPower = powers and 0xF
                        val sidePower = (powers ushr 4) and 0xF

                        if (hasFarInput == 1) {
                            if (backPower < 15) {
                                backPower = farInputSs
                            }
                        }

                        val newOutputSs = if (mode == 0) {
                            // Compare
                            backPower * (sidePower <= backPower).int
                        } else {
                            // Subtract
                            //println("subtract branch")
                            max(0, backPower - sidePower)
                        }

                        // Final write back
                        //println("new output ss ${newOutputSs}")
                        nextNodeDynData = ShrimpleComparatorNode.setDynDataOutputSs(nextNodeDynData, newOutputSs)

                        // Final write back
                        graphBuf[nodeIdx] = getNextNodeIntWithDynDataBits(nodeInt, nextNodeDynData)
                        timestampArray[nodeIdx] = currentRsTick

                        // Updates
                        updateOutputs = oldOutputSs != newOutputSs
                    }

                    else -> {
                        // User input nodes and constants don't change at all
                    }
                }


                // Update outputs
                if (updateOutputs == true) {
                    this.sendUpdatesToNodeOutputs(nodeIdx, nextUpdateDynArrays)
                }
            }

            /*println("===== GRAPH AFTER TICK")
            for (i in graphBuffer.indices) {
                println(toBitString(graphBuffer[i]))
            }*/

            // End of tick
            this.handleUserInputs(nextUpdateDynArrays)
            for (dynArr in currUpdateDynArrays) {
                dynArr.clear()
            }

            // Always at the end
            currentRsTick += 1
        }

    }

    private fun sendUpdatesToNodeOutputs(nodeIdx: Int, nextUpdateDynArrays: Array<DynIntArray>) {
        val edgePointerDataBaseIdx = nodeIdx * 3
        val outputEdgeStart = edgePointerArray[edgePointerDataBaseIdx + 1]
        val outputEdgeCount = (edgePointerArray[edgePointerDataBaseIdx + 2] ushr 16) and 0xFFFF

        for (i in 0 until outputEdgeCount) {
            val nodeIdxToUpdatePointer = edgeArray[outputEdgeStart + i]
            val nodeIdxToUpdate = nodeIdxToUpdatePointer ushr 6
            // TODO: do something with the distance later
            val pointerDist = nodeIdxToUpdatePointer and 0xF

            val updatePriority = (nodeIdxToUpdatePointer ushr 4) and 0x3

            val channel = nextUpdateDynArrays[updatePriority]

            //println("= Thinking of adding node: ${nodeIdxToUpdate}")

            if (!wasNodeUpdateAdded(nodeIdxToUpdate)) {
                addNodeToBitmapAndDynArray(nodeIdxToUpdate, channel)
                //setNodeUpdateAdded(nodeIdxToUpdate)
                //println("= Node: ${nodeIdxToUpdate} was added!")
                //nextUpdateDynArray.add(nodeIdxToUpdate)
            } else {
                //println("= Node: ${nodeIdxToUpdate} was *not* added'}")
            }
        }
    }

    private fun getNodeInputPowers(nodeIdx: Int, nodeUpdatePriority: Int): Int {
        //println("== Getting input powers for node ${nodeIdx}")
        var backPower = 0
        var sidePower = 0

        val edgePointerDataBaseIdx = nodeIdx * 3
        val inputEdgeStart = edgePointerArray[edgePointerDataBaseIdx]
        val inputEdgeCount = (edgePointerArray[edgePointerDataBaseIdx + 2] ushr 0) and 0xFFFF

        for (i in 0 until inputEdgeCount) {
            val inputNodeIdxPointer = edgeArray[inputEdgeStart + i]

            val inputNodeIdx = inputNodeIdxPointer ushr 5


            val dist = inputNodeIdxPointer and 0xF
            val sideBit = (inputNodeIdxPointer ushr 4) and 0x1

            val inputNodeInt = graphBuffer[inputNodeIdx]
            val inputNodeUpdatePriority = ShrimpleNodeIntRepr.getIntPriority(inputNodeInt)
            //println("   My input is ${inputNodeIdx} with timestamp: ${inputNodeTimestamp} (currentTick is ${currentTick})")

            val correctDynBits: Int
            if (inputNodeUpdatePriority < nodeUpdatePriority) {
                // The input we're reading from was updated before us, so we
                // get its most recent state
                correctDynBits = ShrimpleNodeIntRepr.getIntParityPointedDataBits(inputNodeInt)
            } else {
                // Else get the correct priority value (order agnostic sampling)
                val inputNodeTimestamp = timestampArray[inputNodeIdx]
                if (inputNodeTimestamp == currentRsTick) {
                    correctDynBits = ShrimpleNodeIntRepr.getIntNotParityPointedDataBits(inputNodeInt)
                } else {
                    correctDynBits = ShrimpleNodeIntRepr.getIntParityPointedDataBits(inputNodeInt)
                }
            }


            val nodeType = ShrimpleNodeIntRepr.getIntCurrentType(inputNodeInt)
            val power = getNodePower(nodeType, correctDynBits)

            val depletedPower = max(0, power - dist)
            //println("      Read power is: ${power} and depleted power is ${depletedPower}")
            if (sideBit == 0) {
                backPower = max(backPower, depletedPower)
            } else {
                //println("side (depleted) = ${depletedPower}")
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

            ShrimpleNodeType.REPEATER.int -> {
                return ShrimpleRepeaterNode.getPower(dynBits)
            }

            ShrimpleNodeType.COMPARATOR.int -> {
                return ShrimpleComparatorNode.getPower(dynBits)
            }

            ShrimpleNodeType.USER_INPUT.int -> {
                return ShrimpleUserInputNode.getPower(dynBits)
            }

            ShrimpleNodeType.CONSTANT.int -> {
                return ShrimpleConstantNode.getPower(dynBits)
            }

            else -> {
                throw Exception("Node type of id ${nodeType} not recognized, " +
                        "or is lamp in which case it also shouldn't work.")
            }
        }
    }

    private fun addNodeToBitmapAndDynArray(nodeIdx: Int, dynArray: DynIntArray) {
        setNodeUpdateAdded(nodeIdx)
        dynArray.add(nodeIdx)
    }

    private fun addNodeToBitmapAndDynArrayIfNotAlready(nodeIdx: Int, dynArray: DynIntArray) {
        if (!wasNodeUpdateAdded(nodeIdx)) {
            setNodeUpdateAdded(nodeIdx)
            dynArray.add(nodeIdx)
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