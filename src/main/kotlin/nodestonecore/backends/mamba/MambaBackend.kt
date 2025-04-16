package com.sloimay.nodestonecore.backends.mamba

import com.sloimay.mcvolume.IntBoundary
import com.sloimay.mcvolume.McVolume
import com.sloimay.mcvolume.block.BlockState
import com.sloimay.smath.vectors.IVec3
import com.sloimay.nodestonecore.backends.RedstoneSimBackend
import com.sloimay.nodestonecore.backends.mamba.graph.*
import com.sloimay.threadstonecore.backends.mamba.graph.*
import com.sloimay.nodestonecore.backends.mamba.graph.nodes.MambaNode
import com.sloimay.nodestonecore.backends.mamba.graph.nodes.MambaNode.Companion.IntRepr.Companion.getDataBits
import com.sloimay.nodestonecore.backends.mamba.graph.nodes.MambaNodeType
import com.sloimay.nodestonecore.backends.mamba.graph.nodes.MambaUserInputNode
import com.sloimay.nodestonecore.helpers.NsUtils.Companion.toBitString
import com.sloimay.nodestonecore.redstoneir.RedstoneBuildIR
import com.sloimay.nodestonecore.redstoneir.from.fromVolume
import com.sloimay.nodestonecore.shader.ShaderPreproc
import org.lwjgl.opengl.GL43.*
import kotlin.math.ceil


private const val WORK_GROUP_SIZE = 256


private abstract class MambaScheduledAction
private class MambaScheduledUserInput(val inputNode: MambaUserInputNode, val powerToSet: Int) : MambaScheduledAction()


/**
 *
 * New layout for a gpu backend
 *
 */
class MambaBackend private constructor(
    volume: McVolume,
    simBounds: IntBoundary,


    val graph: MambaGraph,
    val positionedNodes: HashMap<IVec3, MambaNode>,
    val nodeIdxToPosition: Array<IVec3?>,
    val positionedUserInputNodes: HashMap<IVec3, MambaUserInputNode>,

    val graphBufferStart: IntArray,
    val graphBufferStartSize: Int,

    val inputArrayStart: IntArray,
    val inputArrayStartSize: Int,


    val iterCountGlUn: Int,

    val tickGlShader: Int,
    val tickGlProgram: Int,
    val graphBufferGlSsbo: Int,
    val inputArrayGlSsbo: Int,

    ) : RedstoneSimBackend(volume, simBounds) {

    // TODO: Known bug: ticks elapsed can overflow after only a few days of leaving a sim running fast
    //       It's kind of unlikely, and won't break parity, but it'd be good to fix at some point
    var ticksElapsed = 0

    var nodeDataLastVisualUpdate = IntArray(graph.nodes.size)
    var nodeDataLastVisualUpdateTicksElapsed = ticksElapsed
    var nodeChangeArray = BooleanArray(nodeDataLastVisualUpdate.size) { true }

    // Gets filled when reading from the gpu
    var gpuGraphBufferReads = IntArray(graphBufferStart.size)

    // Timestamp -> IoScheduleEntry
    private val userInputScheduler: HashMap<Int, MutableList<MambaScheduledUserInput>> = hashMapOf()




    companion object {

        fun new(vol: McVolume, simVolBounds: IntBoundary): MambaBackend {
            val irGraph = RedstoneBuildIR.fromVolume(vol)
            val convertionResult = MambaGraph.fromIR(irGraph)
            val graph = convertionResult.graph
            val positionedNodes = convertionResult.positionedNodes
            val positionedUserInputNodes = convertionResult.positionedUserInputNodes

            val (serializedNodeArray, serializedInputArray) = graph.serialize()

            // # Graph SSBO
            val graphBufferStart = IntArray(serializedNodeArray.size) { serializedNodeArray[it] }
            val graphBufferGlSsbo = glGenBuffers()
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, graphBufferGlSsbo)
            glBufferData(GL_SHADER_STORAGE_BUFFER, graphBufferStart, GL_DYNAMIC_DRAW)
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, graphBufferGlSsbo)

            // # Input array SSBO
            val inputArrayStart = IntArray(serializedInputArray.size) { serializedInputArray[it] }
            val inputArrayGlSsbo = glGenBuffers()
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, inputArrayGlSsbo)
            glBufferData(GL_SHADER_STORAGE_BUFFER, inputArrayStart, GL_STATIC_DRAW)
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, inputArrayGlSsbo)

            // # Make shader program
            val macros = hashMapOf(
                "constantNodeId" to "${MambaNodeType.CONSTANT.int}",
                "repeaterNodeId" to "${MambaNodeType.REPEATER.int}",
                "torchNodeId" to "${MambaNodeType.TORCH.int}",
                "comparatorNodeId" to "${MambaNodeType.COMPARATOR.int}",
                "userInputNodeId" to "${MambaNodeType.USER_INPUT.int}",
                "lampNodeId" to "${MambaNodeType.LAMP.int}",

                "NODE_COUNT" to "${graph.nodes.size}",
                "WORK_GROUP_SIZE" to "$WORK_GROUP_SIZE",
                "NODE_LEN_IN_ARRAY" to "$MAMBA_NODE_LEN_IN_ARRAY",
                "NODE_DATA_DO_UPDATE_BIT_COUNT" to "$MAMBA_DO_UPDATE_BIT_LEN",
                "NODE_TYPE_BIT_COUNT" to "$MAMBA_TYPE_BIT_LEN",
                "NODE_DATA_BASE_MASK" to "$MAMBA_DATA_BASE_MASK",
                "NODE_DATA_SHIFT" to "$MAMBA_DATA_SHIFT",
                "NODE_INPUT_COUNT_SHIFT" to "$MAMBA_INPUT_COUNT_SHIFT",
            )
            var shaderTickSource = object {}.javaClass.getResource("/gpubackend/shaders/mamba/tick.glsl")?.readText()!!
            shaderTickSource = ShaderPreproc.preprocess(shaderTickSource, macros)

            val tickGlShader = glCreateShader(GL_COMPUTE_SHADER)
            glShaderSource(tickGlShader, shaderTickSource)
            glCompileShader(tickGlShader)
            if (glGetShaderi(tickGlShader, GL_COMPILE_STATUS) != GL_TRUE) {
                val log = glGetShaderInfoLog(tickGlShader, 10_000)
                println("Shader compilation failed:\n$log")
            }

            val tickGlProgram = glCreateProgram()
            glAttachShader(tickGlProgram, tickGlShader)
            glLinkProgram(tickGlProgram)
            if (glGetProgrami(tickGlProgram, GL_LINK_STATUS) != GL_TRUE) {
                val log = glGetProgramInfoLog(tickGlProgram, 10_000)
                println("Shader compilation failed:\n$log")
            }

            // # Uniforms
            val iterCountGlUn = glGetUniformLocation(tickGlProgram, "iterCount")

            // # Make the IDX -> position map
            val nodeIdxToPosition = Array(graph.nodes.size) { graph.nodes[it].pos }


            return MambaBackend(
                volume = vol,
                simBounds = simVolBounds,


                graph,
                positionedNodes,
                nodeIdxToPosition,
                positionedUserInputNodes,

                graphBufferStart,
                graphBufferStart.size,

                inputArrayStart,
                inputArrayStart.size,

                iterCountGlUn,


                tickGlShader,
                tickGlProgram,
                graphBufferGlSsbo,
                inputArrayGlSsbo,
            )
        }

    }

    override fun tickWhile(pred: () -> Boolean) {
        //println("======= DATA BEFORE TICKS:")
        //this.printGraphAndInputData()

        glFinish()
        glUseProgram(tickGlProgram)
        val numGroupsX = ceil(graph.nodes.size.toDouble() / WORK_GROUP_SIZE.toDouble()).toInt()

        while (pred()) {
            glUniform1ui(iterCountGlUn, ticksElapsed)
            glDispatchCompute(numGroupsX, 1, 1)
            glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT)

            if ((ticksElapsed and 0x7F) == 0) {
                glFinish()
            }

            this.handleUserInput()
            ticksElapsed += 1
        }

        glFinish()

        //println("======= AFTER BEFORE TICKS:")
        //this.printGraphAndInputData()

    }



    fun getParity(ticksElapsed: Int) = ticksElapsed % 2



    override fun updateRepr(
        updateVolume: Boolean,
        onlyNecessaryVisualUpdates: Boolean,
        renderCallback: (renderPos: IVec3, newBlockState: BlockState) -> Unit
    ) {
        this.readGraphBufferBackFromGpu()

        if (onlyNecessaryVisualUpdates) {
            val parityThisTick = getParity(ticksElapsed)
            val parityLastUpdate = getParity(nodeDataLastVisualUpdateTicksElapsed)

            for (nodeIdx in this.graph.nodes.indices) {
                val nodeIdxInSerializedArray = nodeIdx * MAMBA_NODE_LEN_IN_ARRAY
                val nodeIntLastUpdate = nodeDataLastVisualUpdate[nodeIdx]
                val nodeIntNow = gpuGraphBufferReads[nodeIdxInSerializedArray + parityThisTick]
                nodeDataLastVisualUpdate[nodeIdx] = nodeIntNow

                val nodeIntLastUpdateDBits = getDataBits(nodeIntLastUpdate)
                val nodeIntNowDBits = getDataBits(nodeIntNow)

                //val nodeDataLastUpdate = MambaNode.Companion.IntRepr.getDataBitsAtParity(nodeIntLastUpdate, parityLastUpdate)
                //val nodeDataNow = MambaNode.Companion.IntRepr.getDataBitsAtParity(nodeIntNow, parityThisTick)

                /*
                TODO: Can be improved. For example, comparators changing their output SS from 15 to 14 don't need
                      a visual update. Same for repeaters that are staying on but have their scheduler changing.
                 */
                nodeChangeArray[nodeIdx] = nodeIntLastUpdateDBits != nodeIntNowDBits
            }

            // Last update schtuffs
            this.nodeDataLastVisualUpdateTicksElapsed = ticksElapsed
        } else {
            // Trick the code to update every node
            nodeChangeArray.fill(true)
        }

        /*println("==== NODE CHANGE ARRAY:")
        for (b in nodeChangeArray) {
            println(b)
        }*/

        val ioOnly = true
        val thisTickParity = getParity(ticksElapsed)
        for (nodeIdx in this.graph.nodes.indices) {
            if (nodeChangeArray[nodeIdx] == false) continue
            val nodeSerIdx = nodeIdx * MAMBA_NODE_LEN_IN_ARRAY
            val nodeInt = gpuGraphBufferReads[nodeSerIdx + thisTickParity]
            val nodeType = MambaNode.Companion.IntRepr.getTypeBits(nodeInt)
            val isIoNode = nodeType == MambaNodeType.USER_INPUT.int || nodeType == MambaNodeType.LAMP.int
            if (ioOnly && !isIoNode) continue
            val position = this.nodeIdxToPosition[nodeIdx] ?: continue

            val bs = volume.getBlock(position).state
            val bsMut = bs.toMutable()

            val nodeData = getDataBits(nodeInt)

            // Modify bs
            when (nodeType) {
                MambaNodeType.COMPARATOR.int -> {
                    val outputSs = (nodeData and 0xF)
                    bsMut.setProp("powered", if (outputSs > 0) "true" else "false")
                }
                MambaNodeType.USER_INPUT.int -> {
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
                MambaNodeType.REPEATER.int -> {
                    // Output bool is the first bit of the scheduler
                    val poweredBit = nodeData and 0x1
                    bsMut.setProp("powered", if (poweredBit == 1) "true" else "false")
                }
                MambaNodeType.TORCH.int, MambaNodeType.LAMP.int -> {
                    val poweredBit = nodeData and 0x1
                    //println("torch at ${position} read a: ${poweredBit}, nodeData: ${nodeData}")
                    bsMut.setProp("lit", if (poweredBit == 1) "true" else "false")
                }
                else -> {

                }
            }

            val newBs = bsMut.toImmutable()

            if (updateVolume) {
                /* TODO: Very very bad performance */
                val newVolB = volume.getEnsuredPaletteBlock(newBs)
                volume.setBlock(position, newVolB)
            }

            renderCallback(position, newBs)
        }
    }

    override fun getInputNodePositions(): Set<IVec3> = positionedUserInputNodes.keys


    override fun scheduleButtonPress(ticksFromNow: Int, pressLength: Int, inputNodePos: IVec3) {
        this.scheduleUserInputChange(ticksFromNow, inputNodePos, 15)
        this.scheduleUserInputChange(ticksFromNow + pressLength, inputNodePos, 0)
    }

    override fun scheduleUserInputChange(ticksFromNow: Int, inputNodePos: IVec3, power: Int) {
        val inputNode = this.positionedUserInputNodes[inputNodePos]
            ?: throw Exception("Inputted node position $inputNodePos is not an input node.")

        val tickTimestamp = ticksElapsed + ticksFromNow
        userInputScheduler.putIfAbsent(tickTimestamp, mutableListOf())
        val inputChangesThisTick = userInputScheduler[tickTimestamp]!!
        inputChangesThisTick.add(MambaScheduledUserInput(inputNode, power))
    }


    private fun handleUserInput() {

        //println("Handling user input ..")

        // Remove schedules that may be outdated but there shouldn't be any
        val outdatedTicks = mutableListOf<Int>()
        for ((tick, actions) in this.userInputScheduler) {
            if (tick < ticksElapsed) {
                outdatedTicks.add(tick)
            }
        }
        outdatedTicks.forEach { this.userInputScheduler.remove(it) }

        val actionsThisTick = this.userInputScheduler.remove(ticksElapsed) ?: return
        readGraphBufferBackFromGpu()

        // Update every input node
        for (action in actionsThisTick) {
            val power = action.powerToSet
            val nodeSerializedArrIdx = action.inputNode.idxInSerializedArray

            // Set both bit fields to the inputted power
            var nodeInt1 = gpuGraphBufferReads[nodeSerializedArrIdx]
            var nodeInt2 = gpuGraphBufferReads[nodeSerializedArrIdx + 1]
            val ssLoc = MAMBA_TYPE_BIT_LEN + MAMBA_DO_UPDATE_BIT_LEN
            nodeInt1 = nodeInt1 and (0xF shl ssLoc).inv()
            nodeInt1 = nodeInt1 or ((power and 0xF) shl ssLoc)
            nodeInt2 = nodeInt2 and (0xF shl ssLoc).inv()
            nodeInt2 = nodeInt2 or ((power and 0xF) shl ssLoc)

            // Write back
            gpuGraphBufferReads[nodeSerializedArrIdx] = nodeInt1
            gpuGraphBufferReads[nodeSerializedArrIdx + 1] = nodeInt2
        }

        // Write graph back
        writeGraphDataToGpu(gpuGraphBufferReads)
    }




    private fun readGraphBufferBackFromGpu() {
        glFinish()
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, graphBufferGlSsbo)
        glGetBufferSubData(GL_SHADER_STORAGE_BUFFER, 0, gpuGraphBufferReads)
    }

    private fun writeGraphDataToGpu(graphData: IntArray) {
        if (graphData.size != graphBufferStart.size) {
            throw Exception("Inputted graph data isn't of the same size as the graph buffer on the gpu")
        }
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, graphBufferGlSsbo)
        glBufferData(GL_SHADER_STORAGE_BUFFER, graphData, GL_DYNAMIC_DRAW)
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, graphBufferGlSsbo)
    }

    private fun printGraphAndInputData() {
        readGraphBufferBackFromGpu()
        println("==== GRAPH DATA:")
        for (int in gpuGraphBufferReads) {
            println("${toBitString(int)}")
        }
        println("==== INPUT ARRAY")
        for (int in inputArrayStart) {
            println("${toBitString(int)}")
        }
    }

    fun cleanUp() {
        glDeleteBuffers(inputArrayGlSsbo)
        glDeleteBuffers(graphBufferGlSsbo)
        glDeleteProgram(tickGlProgram)
        glDeleteShader(tickGlShader)
    }

}