package com.sloimay.nodestonecore.simulation.backends.shrimple.graph


import com.sloimay.nodestonecore.simulation.backends.shrimple.ShrimpleRenderRsDust
import com.sloimay.nodestonecore.simulation.backends.shrimple.ShrimpleRenderRsDustInput
import com.sloimay.nodestonecore.simulation.backends.shrimple.graph.nodes.*
import com.sloimay.smath.vectors.IVec3
import com.sloimay.nodestonecore.simulation.backends.shrimple.helpers.ShrimpleHelper.Companion.toBitsInt
import com.sloimay.nodestonecore.simulation.backends.shrimple.helpers.int
import com.sloimay.nodestonecore.redstoneir.RedstoneBuildIR
import com.sloimay.nodestonecore.redstoneir.rsirnodes.*


internal class ShrimpleGraphSerResult(
    val graphArray: IntArray,
    val edgePointerArray: IntArray,
    val edgeArray: IntArray,
)

internal data class ShrimpleGraphResult(
    val graph: ShrimpleGraph,
    val positionedNodes: HashMap<IVec3, ShrimpleNode>,
    val positionedUserInputNodes: HashMap<IVec3, ShrimpleUserInputNode>,
    val rsDusts: List<Pair<IVec3, ShrimpleRenderRsDust>>,
)


internal class ShrimpleGraph {

    val nodes = mutableListOf<ShrimpleNode>()

    companion object {

        internal fun fromIR(ir: RedstoneBuildIR): ShrimpleGraphResult {

            val shrimpleGraph = ShrimpleGraph()
            val LOWEST_PRIORITY = 3
            val LOW_PRIORITY = 2
            val HIGH_PRIORITY = 1
            val HIGHEST_PRIORITY = 0

            val rsIrToShrimple = hashMapOf<RsIrNode, ShrimpleNode>()
            val positionedNodes = hashMapOf<IVec3, ShrimpleNode>()
            val positionedUserInputNodes = hashMapOf<IVec3, ShrimpleUserInputNode>()

            for (node in ir.getNodes()) {
                val shrimpleNode = when (node) {
                    is RsIrComparator -> {
                        ShrimpleComparatorNode(
                            node.position,
                            LOWEST_PRIORITY,
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
                            LOWEST_PRIORITY,
                            schedulerBits,
                            node.locked,
                            node.realDelay - 1,
                            node.realDelay,
                        )
                    }
                    is RsIrTorch -> ShrimpleTorchNode(node.position, LOWEST_PRIORITY, node.lit)
                    is RsIrLamp -> ShrimpleLampNode(node.position, LOWEST_PRIORITY, node.lit)
                    is RsIrConstant -> ShrimpleConstantNode(node.position, LOWEST_PRIORITY, node.signalStrength)
                    is RsIrGoldPressurePlate -> ShrimpleUserInputNode(node.position, LOWEST_PRIORITY, node.powered.int * 15)
                    is RsIrIronPressurePlate -> ShrimpleUserInputNode(node.position, LOWEST_PRIORITY,node.powered.int * 15)
                    is RsIrWoodenPressurePlate -> ShrimpleUserInputNode(node.position, LOWEST_PRIORITY, node.powered.int * 15)
                    is RsIrStonePressurePlate -> ShrimpleUserInputNode(node.position, LOWEST_PRIORITY, node.powered.int * 15)
                    is RsIrStoneButton -> ShrimpleUserInputNode(node.position, LOWEST_PRIORITY, node.powered.int * 15)
                    is RsIrWoodenButton -> ShrimpleUserInputNode(node.position, LOWEST_PRIORITY, node.powered.int * 15)
                    is RsIrLever -> ShrimpleUserInputNode(node.position, LOWEST_PRIORITY, node.powered.int * 15)
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
                        ShrimpleOutputEdge(shrimpleNode, input.dist, input.isSide)
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

            // Figure out priorities
            for ((_, shrimpleNode) in rsIrToShrimple) {
                // If comparator or repeater going into a repeater side, give it a lower priority
                if (shrimpleNode is ShrimpleComparatorNode ||
                    shrimpleNode is ShrimpleRepeaterNode
                ) {
                    if (shrimpleNode.outputs.size == 1) {
                        val edge = shrimpleNode.outputs[0]
                        val nodePointedInto = edge.node
                        if (edge.isSide && nodePointedInto is ShrimpleRepeaterNode) {
                            shrimpleNode.updatePriority = 0
                        }
                    }
                }
            }

            // Redstone wires
            val shrimpleRsWires = ir.getRenderedRsWires().map {
                val pos = it.pos
                val startSs = it.startSs
                val inputs = mutableListOf<ShrimpleRenderRsDustInput>()
                for (rsIrInput in it.inputs) {
                    val shrimpleNode = rsIrToShrimple[rsIrInput.node] ?: continue
                    val dist = rsIrInput.dist
                    val shrimpleRsWireInput = ShrimpleRenderRsDustInput(shrimpleNode, dist)
                    inputs.add(shrimpleRsWireInput)
                }
                pos to ShrimpleRenderRsDust(inputs, startSs)
            }

            //println("ir to shrimple graph node size: ${shrimpleGraph.nodes.size}")

            return ShrimpleGraphResult(
                shrimpleGraph,
                positionedNodes,
                positionedUserInputNodes,
                shrimpleRsWires,
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