package com.sloimay.threadstonecore.backends.shrimple.graph

import com.sloimay.threadstonecore.backends.gpubackend.helpers.toInt
import com.sloimay.threadstonecore.backends.shrimple.graph.nodes.*
import com.sloimay.threadstonecore.backends.shrimple.helpers.ShrimpleHelper.Companion.toBitsInt
import com.sloimay.threadstonecore.backends.shrimple.helpers.int
import com.sloimay.threadstonecore.redstoneir.RedstoneBuildIR
import com.sloimay.threadstonecore.redstoneir.rsirnodes.*
import me.sloimay.smath.vectors.IVec3


class ShrimpleGraphSerResult(
    val graphArray: IntArray,
    val edgePointerArray: IntArray,
    val edgeArray: IntArray,
)

data class ShrimpleGraphResult(
    val graph: ShrimpleGraph,
    val positionedNodes: HashMap<IVec3, ShrimpleNode>,
    val positionedUserInputNodes: HashMap<IVec3, ShrimpleUserInputNode>,
)


class ShrimpleGraph {

    val nodes = mutableListOf<ShrimpleNode>()

    companion object {
        fun fromIR(ir: RedstoneBuildIR): ShrimpleGraphResult {

            val shrimpleGraph = ShrimpleGraph()

            val rsIrToShrimple = hashMapOf<RsIrNode, ShrimpleNode>()
            val positionedNodes = hashMapOf<IVec3, ShrimpleNode>()
            val positionedUserInputNodes = hashMapOf<IVec3, ShrimpleUserInputNode>()

            for (node in ir.getNodes()) {
                val shrimpleNode = when (node) {
                    is RsIrComparator -> {
                        ShrimpleComparatorNode(
                            node.position,
                            node.outputSs,
                            node.hasFarInput(),
                            node.farInputSs,
                            node.compMode == RsIrCompMode.SUBTRACT
                        )
                    }
                    is RsIrRepeater -> {
                        val schedulerMask = (1 shl (node.realDelay)) - 1
                        val schedulerBits = -node.powered.int and schedulerMask
                        ShrimpleRepeaterNode(
                            node.position,
                            schedulerBits,
                            node.locked,
                            node.realDelay,
                        )
                    }
                    is RsIrTorch -> ShrimpleTorchNode(node.position, node.lit)
                    is RsIrLamp -> ShrimpleLampNode(node.position, node.lit)
                    is RsIrConstant -> ShrimpleConstantNode(node.position, node.signalStrength)
                    is RsIrGoldPressurePlate -> ShrimpleUserInputNode(node.position, node.powered.int * 15)
                    is RsIrIronPressurePlate -> ShrimpleUserInputNode(node.position, node.powered.int * 15)
                    is RsIrWoodenPressurePlate -> ShrimpleUserInputNode(node.position, node.powered.int * 15)
                    is RsIrStonePressurePlate -> ShrimpleUserInputNode(node.position, node.powered.int * 15)
                    is RsIrStoneButton -> ShrimpleUserInputNode(node.position, node.powered.int * 15)
                    is RsIrWoodenButton -> ShrimpleUserInputNode(node.position, node.powered.int * 15)
                    is RsIrLever -> ShrimpleUserInputNode(node.position, node.powered.int * 15)
                    else -> null
                }
                if (shrimpleNode == null) continue
                shrimpleGraph.nodes.add(shrimpleNode)
                rsIrToShrimple[node] = shrimpleNode
            }

            // Fill node inputs
            for ((rsIrNode, shrimpleNode) in rsIrToShrimple) {
                for (input in rsIrNode.getInputs()) {
                    val shrimpleInputNode = rsIrToShrimple[input.node] ?: continue
                    shrimpleNode.inputs.add(
                        ShrimpleInputEdge(shrimpleInputNode, input.dist, input.linkType == BackwardLinkType.SIDE)
                    )
                }
            }

            // Fill node outputs using known inputs (not using the IR's outputs because it's unstable
            for ((_, shrimpleNode) in rsIrToShrimple) {
                for (input in shrimpleNode.inputs) {
                    input.node.outputs.add(
                        ShrimpleOutputEdge(shrimpleNode, input.dist)
                    )
                }
            }

            // Fill positions and input positions
            for ((rsIrNode, shrimpleNode) in rsIrToShrimple) {
                if (rsIrNode.position != null) {
                    positionedNodes[rsIrNode.position] = shrimpleNode
                    if (shrimpleNode is ShrimpleUserInputNode) {
                        positionedUserInputNodes[rsIrNode.position] = shrimpleNode
                    }
                }
            }

            println("ir to shrimple graph node size: ${shrimpleGraph.nodes.size}")

            return ShrimpleGraphResult(
                shrimpleGraph,
                positionedNodes,
                positionedUserInputNodes,
            )
        }
    }

    fun serialize(): ShrimpleGraphSerResult {

        // Setup idx in array mapping
        for ((i, n) in nodes.withIndex()) n.idxInArray = i

        // Populate graph array
        val graphArray = IntArray(nodes.size) { nodes[it].getIntRepr() }

        // # Populate edge and edge pointer arrays
        val edgePointerList = mutableListOf<Int>()
        val edgeList = mutableListOf<Int>()
        for (n in nodes) {

            val inputEdgeStart = edgeList.size
            for (i in n.inputs) edgeList.add( i.serialize() )

            val outputEdgeStart = edgeList.size
            for (o in n.outputs) edgeList.add( o.serialize() )

            val edgeCounts = toBitsInt(
                n.inputs.size to 16,
                n.outputs.size to 16,
            )

            edgePointerList.add( inputEdgeStart )
            edgePointerList.add( outputEdgeStart )
            edgePointerList.add( edgeCounts )
        }

        return ShrimpleGraphSerResult(graphArray, edgePointerList.toIntArray(), edgeList.toIntArray())
    }

}