package com.sloimay.threadstonecore.backends.gpubackend.gpursgraph.from

import com.sloimay.threadstonecore.backends.gpubackend.gpursgraph.GpuRsGraph
import com.sloimay.threadstonecore.backends.gpubackend.gpursgraph.nodes.*
import com.sloimay.threadstonecore.backends.gpubackend.helpers.toInt
import com.sloimay.threadstonecore.redstoneir.RsIrGraph
import com.sloimay.threadstonecore.redstoneir.rsirnodes.*
import me.sloimay.mcvolume.McVolume
import me.sloimay.smath.clamp
import me.sloimay.smath.vectors.IVec3

class RenderedRsWireInput(val node: GpuRsNode, val dist: Int)
class RenderedRsWire(val inputs: MutableList<RenderedRsWireInput>)

class GpuGraphFromResult(val graph: GpuRsGraph,
                         val nodePositions: HashMap<GpuRsNode, IVec3>,
                         val volume: McVolume,
                         val renderedRsWires: HashMap<IVec3, RenderedRsWire>,
                         val inputNodes: HashMap<IVec3, UserInputNodeGpu>)

fun GpuRsGraph.Companion.fromRsIrGraph(g: RsIrGraph): GpuGraphFromResult {

    val gpuGraph = GpuRsGraph()

    val nodeMappings = hashMapOf<RsIrNode, GpuRsNode>()

    // # Convert nodes, fill their inputs later
    for (node in g.getNodes()) {
        val gNode = when (node) {
            is RsIrComparator -> {
                ComparatorNodeGpu(
                    node.outputSs,
                    node.hasFarInput(),
                    node.farInputSs.clamp(0, 15),
                    node.compMode == ComparatorMode.SUBTRACT
                )
            }
            is RsIrRepeater -> {
                val schedulerMask = (1 shl (node.realDelay)) - 1
                val schedulerBits = -(node.powered).toInt() and schedulerMask
                RepeaterNodeGpu(
                    node.locked,
                    node.realDelay,
                    schedulerBits,
                )
            }
            is RsIrTorch -> TorchNodeGpu(node.lit)
            is RsIrLamp -> LampNodeGpu(node.lit)
            is RsIrConstant -> ConstantNodeGpu(node.signalStrength)
            is RsIrGoldPressurePlate -> UserInputNodeGpu(node.powered.toInt() * 15)
            is RsIrIronPressurePlate -> UserInputNodeGpu(node.powered.toInt() * 15)
            is RsIrWoodenPressurePlate -> UserInputNodeGpu(node.powered.toInt() * 15)
            is RsIrStonePressurePlate -> UserInputNodeGpu(node.powered.toInt() * 15)
            is RsIrStoneButton -> UserInputNodeGpu(node.powered.toInt() * 15)
            is RsIrWoodenButton -> UserInputNodeGpu(node.powered.toInt() * 15)
            is RsIrLever -> UserInputNodeGpu(node.powered.toInt() * 15)
            else -> null
        }
        if (gNode == null) continue
        gpuGraph.addNode(gNode)
        nodeMappings[node] = gNode
    }

    // # Fill node inputs
    for ((rsIrNode, gpuNode) in nodeMappings) {
        for (input in rsIrNode.getInputs()) {
            val gpuInputNode = nodeMappings[input.node] ?: continue
            gpuNode.addInput(gpuInputNode, input.dist, input.linkType == BackwardLinkType.SIDE)
        }
    }

    // # Make node positions
    val nodePositions: HashMap<GpuRsNode, IVec3> = hashMapOf()
    for ((rsIrNode, gpuNode) in nodeMappings) {
        nodePositions[gpuNode] = rsIrNode.position
    }

    // # Make user input nodes hashmap
    val userInputNodes = hashMapOf<IVec3, UserInputNodeGpu>()
    for ((rsIrNode, gpuNode) in nodeMappings) {
        if (gpuNode is UserInputNodeGpu) {
            userInputNodes[rsIrNode.position] = gpuNode
        }
    }


    return GpuGraphFromResult(
        gpuGraph,
        nodePositions,
        g.vol,
        hashMapOf(),
        userInputNodes,
    )

}