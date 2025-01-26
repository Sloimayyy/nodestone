package com.sloimay.threadstonecore.backends.gpubackend

import com.sloimay.threadstonecore.backends.RedstoneSimBackend
import com.sloimay.threadstonecore.backends.gpubackend.gpursgraph.*
import com.sloimay.threadstonecore.backends.gpubackend.gpursgraph.from.RenderedRsWire
import com.sloimay.threadstonecore.backends.gpubackend.gpursgraph.from.fromRsIrGraph
import me.sloimay.mcvolume.IntBoundary
import me.sloimay.mcvolume.McVolume
import com.sloimay.threadstonecore.backends.gpubackend.gpursgraph.nodes.*
import com.sloimay.threadstonecore.redstoneir.RsIrGraph
import com.sloimay.threadstonecore.redstoneir.from.fromVolume
import com.sloimay.threadstonecore.shader.ShaderPreproc
import me.sloimay.smath.clamp
import me.sloimay.smath.vectors.IVec3
import org.lwjgl.opengl.GL43.*
import kotlin.math.ceil
import kotlin.math.max



private const val WORK_GROUP_SIZE = 256


private abstract class ScheduledAction

private class ScheduledUserInput(val inputNode: UserInputNodeGpu, val powerToSet: Int) : ScheduledAction()


/*
class RenderedRsWireInput(val node: RsIrNode, val dist: Int)
class RenderedRsWire(val inputs: MutableList<RenderedRsWireInput>)



class GraphFromResult(val graph: RsGraph,
                      val nodePositions: HashMap<RsIrNode, IVec3>,
                      val volume: McVolume,
                      val renderedRsWires: HashMap<IVec3, RenderedRsWire>,
                      val inputNodes: HashMap<IVec3, RsIrNode>)
 */


/**
 * Standalone simulation based on mcvolume
 */
class RsGpuBackend private constructor(
    val vol: McVolume,
    val volBounds: IntBoundary,
    val graph: GpuRsGraph,

    val nodePositions: HashMap<GpuRsNode, IVec3>,
    val renderedRsWires: HashMap<IVec3, RenderedRsWire>,
    val userInputNodes: HashMap<IVec3, UserInputNodeGpu>,

    val baseSerializedGraph: IntArray,
    val serializedGraphSize: Int,
    val nodeSerializedGraphArrIdx: IntArray,
    val baseDualGraphBuffer: IntArray,

    val tickedGraphIdxGlUniform: Int,
    val receiverGraphIdxGlUniform: Int,

    val tickGlShader: Int,
    val tickGlProgram: Int,
    val graphDualBufferGlSsbo: Int,
    val nodeIndexesGlSsbo: Int,
    //private val inputsBufferGlUbo: Int,

) : RedstoneSimBackend(vol, volBounds) {

    var ticksElapsed: Int = 0
    var renderRedstoneWires = true
    var dualGraphBufferLastUpdate = baseDualGraphBuffer.clone()
    var nodeChangeArray = IntArray(dualGraphBufferLastUpdate.size) { 0 }


    // Timestamp -> IoScheduleEntry
    private val userInputScheduler: HashMap<Int, MutableList<ScheduledUserInput>> = hashMapOf()



    companion object {


        fun new(vol: McVolume,
                simVolBounds: IntBoundary,): RsGpuBackend {

            /*val graph = RsGraph()
            val clockCount = 1
            for (i in 0 until clockCount) {
                run {
                    val torch1 = TorchNode(lit = true)
                    val torch2 = TorchNode(lit = true)
                    val torch3 = TorchNode(lit = false)

                    torch1.addInput(torch3, 0)
                    torch2.addInput(torch1, 0)
                    torch3.addInput(torch2, 0)

                    graph.addNode(torch1)
                    graph.addNode(torch2)
                    graph.addNode(torch3)
                }
            }*/

            val irGraph = RsIrGraph.fromVolume(vol)
            val graphFromResult = GpuRsGraph.fromRsIrGraph(irGraph)
            val graph = graphFromResult.graph
            val nodePositions = graphFromResult.nodePositions
            val renderingRsWires = graphFromResult.renderedRsWires
            val inputNodes = graphFromResult.inputNodes

            /*val nodePositions = hashMapOf<RsNode, NodePosData>()
            for ((node, volPos) in nodeVolPositions) {
                nodePositions[node] = NodePosData(volPos, volPos + simWorldBounds.a)
            }*/


            val (graphArr, nodeArrIndexes) = graph.serialize()

            //println("======== GRAPH ARR (len of ${graphArr.size}):")
            //for (i in graphArr.indices) {
            //    println(graphArr[i])
            //}

            // # Make graph SSBO
            val baseDualGraphBuffer = IntArray(graphArr.size * 2)
            for (i in 0 until graphArr.size) {
                baseDualGraphBuffer[i] = graphArr[i]
                baseDualGraphBuffer[i + graphArr.size] = graphArr[i]
            }

            val graphDualBufferGlSsbo = glGenBuffers()
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, graphDualBufferGlSsbo)
            glBufferData(GL_SHADER_STORAGE_BUFFER, baseDualGraphBuffer, GL_DYNAMIC_DRAW)
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, graphDualBufferGlSsbo)

            // # Make node indexes SSBO
            val nodeIndexesGlSsbo = glGenBuffers()
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, nodeIndexesGlSsbo)
            glBufferData(GL_SHADER_STORAGE_BUFFER, nodeArrIndexes, GL_STATIC_DRAW)
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, nodeIndexesGlSsbo)


            // # Make shader program
            val macros = hashMapOf(
                "nodeCount" to "${graph.nodes.size}",
                "constantId" to "$CONSTANT_ID",
                "repId" to "$REPEATER_ID",
                "torchId" to "$TORCH_ID",
                "comparatorId" to "$COMPARATOR_ID",
                "userInputId" to "$USER_INPUT_NODE_ID",
                "redstoneLampId" to "$LAMP_ID",
                "WORK_GROUP_SIZE" to "$WORK_GROUP_SIZE",
                "NODE_INPUT_COUNT_SHIFT" to "$NODE_INPUT_COUNT_SHIFT",
            )
            var shaderTickSource = object {}.javaClass.getResource("/gpubackend/shaders/tickgraph.glsl")?.readText()!!
            shaderTickSource = ShaderPreproc.preprocess(shaderTickSource, macros)
            //println(shaderTickSource)

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
            val tickedGraphIdxGlUniform = glGetUniformLocation(tickGlProgram, "tickedGraphBase")
            val receiverGraphIdxGlUniform = glGetUniformLocation(tickGlProgram, "receiverGraphBase")

            return RsGpuBackend(
                vol = vol,
                volBounds = simVolBounds,
                graph = graph,
                nodePositions = nodePositions,
                renderedRsWires = renderingRsWires,
                userInputNodes = inputNodes,
                baseSerializedGraph = graphArr,
                serializedGraphSize = graphArr.size,
                nodeSerializedGraphArrIdx = nodeArrIndexes,
                baseDualGraphBuffer = baseDualGraphBuffer,
                tickedGraphIdxGlUniform = tickedGraphIdxGlUniform,
                receiverGraphIdxGlUniform = receiverGraphIdxGlUniform,
                tickGlShader = tickGlShader,
                tickGlProgram = tickGlProgram,
                graphDualBufferGlSsbo = graphDualBufferGlSsbo,
                nodeIndexesGlSsbo = nodeIndexesGlSsbo,
            )
        }
    }

    private fun getTickedGraphBaseIdx() = (ticksElapsed%2) * serializedGraphSize


    fun tickN(tickCount: Int) {
        var i = 0
        tickWhile {
            i++ < tickCount
        }

        // # Logging

        val graphOut = IntArray(baseDualGraphBuffer.size)
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, graphDualBufferGlSsbo)
        glGetBufferSubData(GL_SHADER_STORAGE_BUFFER, 0, graphOut)

        /*println("GRAPH OUT SIZE: ${graphOut.size}")

        for (i in 0 until graphOut.size) {
            println(toBitString(graphOut[i]))
            if (i == graphOut.size / 2 - 1) {
                println()
            }
        }*/

        /*
        for (i in 0 until serializedGraphSize) {
            println(toBitString(graphOut[i]))
        }
        println()

        for (i in 0 until serializedGraphSize) {
            println(toBitString(graphOut[i + serializedGraphSize]))
        }
         */
    }


    override fun tickWhile(pred: () -> Boolean) {
        while (pred()) {

            glUseProgram(tickGlProgram)
            val parity = ticksElapsed%2
            val tickedGraphBase = getTickedGraphBaseIdx()
            val receiverGraphBase = (1-parity) * serializedGraphSize
            glUniform1ui(tickedGraphIdxGlUniform, tickedGraphBase)
            glUniform1ui(receiverGraphIdxGlUniform, receiverGraphBase)
            glDispatchCompute(ceil(graph.nodes.size.toDouble() / WORK_GROUP_SIZE.toDouble()).toInt(), 1, 1)
            glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT)

            if (ticksElapsed % 100 == 0) {
                glFinish()
            }

            // Handle user input after the graph update was done to avoid off-by-one tick powering etc
            this.handleUserInput()


            ticksElapsed += 1
        }
        glFinish()
    }

    override fun render(updateVolume: Boolean, changeCallback: (changedPos: IVec3) -> Unit) {
        TODO("Not yet implemented")
    }

    override fun getInputNodeAt(nodePos: IVec3): UserInputNodeGpu? {
        return this.userInputNodes[nodePos]
    }

    override fun scheduleButtonPress(ticksFromNow: Int, pressLength: Int, inputNode: UserInputNodeGpu) {
        this.scheduleUserInputChange(ticksFromNow, inputNode, 15)
        this.scheduleUserInputChange(ticksFromNow + pressLength, inputNode, 0)
    }

    override fun scheduleUserInputChange(ticksFromNow: Int, inputNode: UserInputNodeGpu, power: Int) {
        val tickTimestamp = ticksElapsed + ticksFromNow

        if (tickTimestamp !in userInputScheduler) {
            userInputScheduler[tickTimestamp] = mutableListOf()
        }
        val inputChangesThisTick = userInputScheduler[tickTimestamp]!!

        inputChangesThisTick.add(ScheduledUserInput(inputNode, power))
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

        // Get this tick's schedules
        val actions = this.userInputScheduler.remove(ticksElapsed) ?: return

        //println("found actions")

        // Get graph data
        val dualBufferedGraphData = readDualBufferedGraphBack()

        // Update every node
        for (action in actions) {
            // Update node
            action.inputNode.power = action.powerToSet

            // # Update graph
            val idxInGraphBuf = nodeSerializedGraphArrIdx[action.inputNode.arrayIdx]
            var nodeInt = dualBufferedGraphData[idxInGraphBuf]
            // Erase the 4 bits for the power
            nodeInt = (nodeInt and (0xF shl 4).inv())
            // Write the 4 bits of the new power
            nodeInt = (nodeInt or ((action.powerToSet and 0xF) shl 4))
            // Write back to both nodes in the dual buffer
            dualBufferedGraphData[idxInGraphBuf] = nodeInt
            dualBufferedGraphData[idxInGraphBuf + serializedGraphSize] = nodeInt
        }

        // Write graph back
        writeDualBufferedGraph(dualBufferedGraphData)

    }

    fun isPosUserInputNode(nodePos: IVec3): Boolean = nodePos in this.userInputNodes

    fun scheduleButtonPress(nodePos: IVec3, ticksFromNow: UInt, length: UInt) {
        this.scheduleUserInputChange(ticksFromNow, nodePos, 15)
        this.scheduleUserInputChange(ticksFromNow + length, nodePos, 0)
    }

    fun scheduleUserInputChange(ticksFromNow: UInt, nodePos: IVec3, power: Int) {
        if (!this.isPosUserInputNode(nodePos)) {
            println("[ERROR] Silent error while trying to schedule a user input change: $nodePos isn't a user input node.")
        }
        val node = this.userInputNodes[nodePos]!!
        val tickTimestamp = ticksElapsed.toUInt() + ticksFromNow

        if (tickTimestamp.toInt() !in userInputScheduler) {
            userInputScheduler[tickTimestamp.toInt()] = mutableListOf()
        }
        val inputChangesThisTick = userInputScheduler[tickTimestamp.toInt()]!!

        inputChangesThisTick.add(ScheduledUserInput(node, power))
    }


    fun updateVol() {
        // # Flood the nodes of this graph with the new node data
        // Read the graph buffer back
        val graphDualBufferOut = readDualBufferedGraphBack()
        val baseIdx = getTickedGraphBaseIdx()
        val graphOut = graphDualBufferOut.sliceArray(baseIdx until (baseIdx + graphDualBufferOut.size / 2))
        /*for (i in this.graph.nodes.indices) {
            val nodeIdx = nodeSerializedGraphArrIdx[i]
            val nodeLastUpdate = dualGraphBufferLastUpdate[baseIdx + nodeIdx]
            val nodeNow = graphDualBufferOut[baseIdx + nodeIdx]
            nodeChangeArray[nodeIdx] = ((nodeLastUpdate xor nodeNow) != 0).toInt()
        }*/

        // Graph deser
        this.graph.deserializeInto(graphOut, nodeSerializedGraphArrIdx, nodeChangeArray)

        // # Place blocks
        for ((node, position) in this.nodePositions) {
            val bs = vol.getBlock(position).state
            val newBs = node.changeBlockState(bs)
            val newVolB = vol.getPaletteBlock(newBs)
            vol.setBlock(position, newVolB)
        }

        // # Render redstone wires
        if (this.renderRedstoneWires) {
            for ((wirePos, rsWire) in this.renderedRsWires) {
                // Figure ss of this redstone
                val inputs = rsWire.inputs
                var ss = 0
                for (input in inputs) {
                    val inputSs = input.node.getSs()
                    val depleted = (inputSs - input.dist).clamp(0, 15)
                    ss = max(depleted, ss)
                }

                // Place
                val bs = vol.getBlock(wirePos).state
                val bsMut = bs.toMutable()
                bsMut.setProp("power", ss.toString())
                val newBs = bsMut.toImmutable()
                val newVolB = vol.getPaletteBlock(newBs)
                vol.setBlock(wirePos, newVolB)
            }
        }

        dualGraphBufferLastUpdate = graphDualBufferOut
    }



    private fun readDualBufferedGraphBack(): IntArray {
        val graphDualBufferOut = IntArray(baseDualGraphBuffer.size)
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, graphDualBufferGlSsbo)
        glGetBufferSubData(GL_SHADER_STORAGE_BUFFER, 0, graphDualBufferOut)
        return graphDualBufferOut
    }

    private fun writeDualBufferedGraph(graphData: IntArray) {
        if (graphData.size != baseDualGraphBuffer.size) {
            throw Exception("Inputted graph data isn't of the same size as the graph buffer on the gpu")
        }
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, graphDualBufferGlSsbo)
        glBufferData(GL_SHADER_STORAGE_BUFFER, graphData, GL_DYNAMIC_DRAW)
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, graphDualBufferGlSsbo)
    }



    fun cleanUp() {
        glDeleteBuffers(nodeIndexesGlSsbo)
        glDeleteBuffers(graphDualBufferGlSsbo)
        glDeleteProgram(tickGlProgram)
        glDeleteProgram(tickGlShader)
    }
}