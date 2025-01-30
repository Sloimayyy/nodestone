package com.sloimay.threadstonecore.backends.gpubackend.gpursgraph.from

import com.sloimay.threadstonecore.backends.gpubackend.gpursgraph.GpuRsGraph
import com.sloimay.threadstonecore.backends.gpubackend.helpers.toInt
import com.sloimay.threadstonecore.backends.gpubackend.gpursgraph.nodes.ComparatorNodeGpu
import com.sloimay.threadstonecore.backends.gpubackend.gpursgraph.nodes.ConstantNodeGpu
import com.sloimay.threadstonecore.backends.gpubackend.gpursgraph.nodes.GpuRsNode
import com.sloimay.threadstonecore.backends.gpubackend.gpursgraph.nodes.LampNodeGpu
import com.sloimay.threadstonecore.backends.gpubackend.gpursgraph.nodes.RepeaterNodeGpu
import com.sloimay.threadstonecore.backends.gpubackend.gpursgraph.nodes.TorchNodeGpu
import com.sloimay.threadstonecore.backends.gpubackend.gpursgraph.nodes.UserInputNodeGpu
import com.sloimay.threadstonecore.redstoneir.RedstoneBuildIR
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



fun GpuRsGraph.Companion.fromRsIr(g: RedstoneBuildIR): GpuGraphFromResult {

    val gpuGraph = GpuRsGraph()

    val nodeMappings = hashMapOf<RsIrNode, GpuRsNode>()
    val renderedWires = hashMapOf<IVec3, RenderedRsWire>()

    // # Convert nodes, fill their inputs later
    for (node in g.getNodes()) {
        val gNode = when (node) {
            is RsIrComparator -> {
                ComparatorNodeGpu(
                    node.outputSs,
                    node.hasFarInput(),
                    node.farInputSs.clamp(0, 15),
                    node.compMode == RsIrCompMode.SUBTRACT
                )
            }
            is RsIrRepeater -> {
                val schedulerMask = (1 shl (node.realDelay)) - 1
                val schedulerBits = -node.powered.toInt() and schedulerMask
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
        if (rsIrNode.position != null) {
            nodePositions[gpuNode] = rsIrNode.position
        }
    }

    // # Make user input nodes hashmap
    val userInputNodes = hashMapOf<IVec3, UserInputNodeGpu>()
    for ((rsIrNode, gpuNode) in nodeMappings) {
        if (gpuNode is UserInputNodeGpu) {
            if (rsIrNode.position != null) {
                userInputNodes[rsIrNode.position] = gpuNode
            }
        }
    }

    // # Convert rendered redstone wires
    for (rsw in g.getRenderedRsWires()) {
        val rswGpu = RenderedRsWire(mutableListOf())
        for (i in rsw.inputs) {
            rswGpu.inputs.add(RenderedRsWireInput(nodeMappings[i.node]!!, i.dist))
        }
        renderedWires[rsw.pos] = rswGpu
    }

    return GpuGraphFromResult(
        gpuGraph,
        nodePositions,
        g.vol,
        renderedWires,
        userInputNodes,
    )
}