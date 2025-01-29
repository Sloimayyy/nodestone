package com.sloimay.threadstonecore.backends.mamba

import com.sloimay.threadstonecore.backends.RedstoneSimBackend
import com.sloimay.threadstonecore.backends.mamba.graph.*
import com.sloimay.threadstonecore.backends.mamba.graph.nodes.MambaNode
import com.sloimay.threadstonecore.backends.mamba.graph.nodes.MambaNodeType
import com.sloimay.threadstonecore.backends.mamba.graph.nodes.MambaUserInputNode
import com.sloimay.threadstonecore.helpers.ThscUtils.Companion.toBitString
import com.sloimay.threadstonecore.redstoneir.RedstoneBuildIR
import com.sloimay.threadstonecore.redstoneir.from.fromVolume
import com.sloimay.threadstonecore.shader.ShaderPreproc
import me.sloimay.mcvolume.IntBoundary
import me.sloimay.mcvolume.McVolume
import me.sloimay.mcvolume.block.BlockState
import me.sloimay.smath.vectors.IVec3
import org.lwjgl.opengl.GL20
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
    var nodeChangeArray = BooleanArray(nodeDataLastVisualUpdate.size)

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
                "WORK_GROUP_SIZE" to "${WORK_GROUP_SIZE}",
                "NODE_LEN_IN_ARRAY" to "${MAMBA_NODE_LEN_IN_ARRAY}",
                "NODE_DATA_BIT_COUNT" to "${MAMBA_DATA_BIT_LEN}",
                "NODE_DATA_DO_UPDATE_BIT_COUNT" to "${MAMBA_DO_UPDATE_BIT_LEN}",
                "NODE_TYPE_BIT_COUNT" to "${MAMBA_TYPE_BIT_LEN}",
                "NODE_DATA_BITS_BASE_MASK" to "${(1 shl MAMBA_DATA_BIT_LEN) - 1}",
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
            val iterCountGlUn = GL20.glGetUniformLocation(tickGlProgram, "iterCount")

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
        /*println("======= DATA BEFORE TICK:")
        this.printGraphAndInputData()
        */
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

        /*println("======= AFTER BEFORE TICK:")
        this.printGraphAndInputData()
         */
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
                val nodeIntNow = gpuGraphBufferReads[nodeIdxInSerializedArray]

                val nodeDataLastUpdate = MambaNode.Companion.IntRepr.getDataBitsAtParity(nodeIntLastUpdate, parityLastUpdate)
                val nodeDataNow = MambaNode.Companion.IntRepr.getDataBitsAtParity(nodeIntNow, parityThisTick)

                /*
                TODO: Can be improved. For example, comparators changing their output SS from 15 to 14 don't need
                      a visual update. Same for repeaters that are staying on but have their scheduler changing.
                 */
                nodeChangeArray[nodeIdx] = nodeDataLastUpdate != nodeDataNow

            }

            // Set graph data from last update
            for (nodeIdx in this.graph.nodes.indices) {
                val nodeIdxInSerializedArray = nodeIdx * MAMBA_NODE_LEN_IN_ARRAY
                nodeDataLastVisualUpdate[nodeIdx] = gpuGraphBufferReads[nodeIdxInSerializedArray]
            }
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
            val nodeInt = gpuGraphBufferReads[nodeSerIdx]
            val nodeType = MambaNode.Companion.IntRepr.getTypeBits(nodeInt)
            val isIoNode = nodeType == MambaNodeType.USER_INPUT.int || nodeType == MambaNodeType.LAMP.int
            if (ioOnly && !isIoNode) continue
            val position = this.nodeIdxToPosition[nodeIdx] ?: continue

            val bs = volume.getBlock(position).state
            val bsMut = bs.toMutable()

            val nodeData = MambaNode.Companion.IntRepr.getDataBitsAtParity(nodeInt, thisTickParity)

            // Modify bs
            when (nodeType) {
                MambaNodeType.CONSTANT.int, MambaNodeType.COMPARATOR.int -> {
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

        // Update every node
        for (action in actionsThisTick) {
            val power = action.powerToSet
            val nodeSerializedArrIdx = action.inputNode.idxInSerializedArray

            // Set both bit fields to the inputted power
            var nodeInt = gpuGraphBufferReads[nodeSerializedArrIdx]
            val firstSsLoc = MAMBA_TYPE_BIT_LEN + MAMBA_DO_UPDATE_BIT_LEN
            val secondSsLoc = firstSsLoc + MAMBA_DATA_BIT_LEN + MAMBA_DO_UPDATE_BIT_LEN
            nodeInt = nodeInt and (0xF shl firstSsLoc).inv()
            nodeInt = nodeInt or ((power and 0xF) shl firstSsLoc)
            nodeInt = nodeInt and (0xF shl secondSsLoc).inv()
            nodeInt = nodeInt or ((power and 0xF) shl secondSsLoc)

            // Write back
            gpuGraphBufferReads[nodeSerializedArrIdx] = nodeInt
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