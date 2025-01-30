package com.sloimay.threadstonecore.backends.mamba.graph

import com.sloimay.threadstonecore.backends.mamba.graph.nodes.*
import com.sloimay.threadstonecore.backends.mamba.helpers.MambaHelper.Companion.toBitsInt
import com.sloimay.threadstonecore.backends.mamba.helpers.toInt
import com.sloimay.threadstonecore.redstoneir.RedstoneBuildIR
import com.sloimay.threadstonecore.redstoneir.rsirnodes.*
import me.sloimay.smath.clamp
import me.sloimay.smath.vectors.IVec3

/**
 * Data bits = the 13 bits of data that each node have, without the "do update" part as it's common to every node.
 */

const val MAMBA_TYPE_BIT_LEN = 4

const val MAMBA_DO_UPDATE_BIT_LEN = 1

const val MAMBA_INPUT_COUNT_BIT_LEN = 12

const val MAMBA_DATA_BIT_LEN = 32 - MAMBA_INPUT_COUNT_BIT_LEN - MAMBA_TYPE_BIT_LEN - MAMBA_DO_UPDATE_BIT_LEN
const val MAMBA_DATA_SHIFT = MAMBA_TYPE_BIT_LEN + MAMBA_DO_UPDATE_BIT_LEN
const val MAMBA_DATA_BASE_MASK = (1 shl MAMBA_DATA_BIT_LEN) - 1

const val MAMBA_INPUT_COUNT_SHIFT = MAMBA_DATA_SHIFT + MAMBA_DATA_BIT_LEN




const val MAMBA_NODE_LEN_IN_ARRAY = 3


class MambaGraph {

    val nodes: MutableList<MambaNode> = mutableListOf()

    fun addNode(node: MambaNode) {
        // In case I wanna do something else when adding nodes too
        nodes.add(node)
    }

    fun serialize(): Pair<IntArray, IntArray> {
        val nodesArray = mutableListOf<Int>()
        val ioArray = mutableListOf<Int>()

        val outputs = hashMapOf<MambaNode, MutableList<MambaNode>>()
        // Get the node outputs
        for (n in nodes) outputs[n] = mutableListOf()
        for (n in nodes) {
            for (i in n.inputs) {
                outputs[i.node]!!.add(n)
            }
        }

        // Serialize node array first while leaving input pointers empty
        for ((nodeIdx, n) in nodes.withIndex()) {
            n.idxInSerializedArray = nodesArray.size
            n.idxInArray = nodeIdx

            val nodeTypeBits = n.ID.int
            val doUpdate = true // to be determined later
            val dataBits = n.getDataBits()

            val nodeInt = toBitsInt(
                nodeTypeBits to MAMBA_TYPE_BIT_LEN,
                doUpdate.toInt() to MAMBA_DO_UPDATE_BIT_LEN,
                dataBits to MAMBA_DATA_BIT_LEN,
                n.inputs.size to MAMBA_INPUT_COUNT_BIT_LEN,
            )
            nodesArray.add(nodeInt) // Even iters
            nodesArray.add(nodeInt) // Odd iters
            nodesArray.add(0) // Empty input pointer for now
        }

        // Serialize inputs and outputs
        for (n in nodes) {
            val inputsStartIdx = ioArray.size
            val inputPtrIdx = n.idxInSerializedArray + 2
            val nodeOutputs = outputs[n]!!

            val inputPtr = toBitsInt(
                n.inputs.isNotEmpty().toInt() to 1,
                nodeOutputs.isNotEmpty().toInt() to 1,
                inputsStartIdx to 30,
            )
            nodesArray[inputPtrIdx] = inputPtr

            // Ser inputs
            for ((idx, i) in n.inputs.withIndex()) {
                val redstoneDist = i.dist.clamp(0, 15)
                val isSideInput = i.side
                val nodePtr = i.node.idxInArray

                val inputInt = toBitsInt(
                    redstoneDist to 4,
                    isSideInput.toInt() to 1,
                    nodePtr to 27,
                )
                ioArray.add(inputInt)
            }

            // Ser outputs
            for ((idx, o) in nodeOutputs.withIndex()) {
                val isLastOutput = idx == (nodeOutputs.size-1)
                val nodePtr = o.idxInArray
                val outputInt = toBitsInt(
                    isLastOutput.toInt() to 1,
                    nodePtr to 31,
                )
                ioArray.add(outputInt)
            }
        }

        return nodesArray.toIntArray() to ioArray.toIntArray()
    }




    companion object {


        data class MambaGraphResult(
            val graph: MambaGraph,
            val positionedNodes: HashMap<IVec3, MambaNode>,
            val positionedUserInputNodes: HashMap<IVec3, MambaUserInputNode>,
        )


        fun fromIR(graph: RedstoneBuildIR): MambaGraphResult {

            val mambaGraph = MambaGraph()

            val rsIrNodeToMamba = hashMapOf<RsIrNode, MambaNode>()
            val positionedNodes = hashMapOf<IVec3, MambaNode>()
            val positionedUserInputNodes = hashMapOf<IVec3, MambaUserInputNode>()

            for (node in graph.getNodes()) {
                val mambaNode = when (node) {
                    is RsIrComparator -> {
                        MambaComparatorNode(
                            node.position,
                            node.outputSs,
                            node.farInputSs,
                            node.compMode == RsIrCompMode.SUBTRACT
                        )
                    }
                    is RsIrRepeater -> {
                        MambaRepeaterNode(
                            node.position,
                            node.powered,
                            node.locked,
                            node.realDelay,
                        )
                    }
                    is RsIrTorch -> MambaTorchNode(node.position, node.lit)
                    is RsIrLamp -> MambaLampNode(node.position, node.lit)
                    is RsIrConstant -> MambaConstantNode(node.position, node.signalStrength)
                    is RsIrGoldPressurePlate -> MambaUserInputNode(node.position, node.powered.toInt() * 15)
                    is RsIrIronPressurePlate -> MambaUserInputNode(node.position, node.powered.toInt() * 15)
                    is RsIrWoodenPressurePlate -> MambaUserInputNode(node.position, node.powered.toInt() * 15)
                    is RsIrStonePressurePlate -> MambaUserInputNode(node.position, node.powered.toInt() * 15)
                    is RsIrStoneButton -> MambaUserInputNode(node.position, node.powered.toInt() * 15)
                    is RsIrWoodenButton -> MambaUserInputNode(node.position, node.powered.toInt() * 15)
                    is RsIrLever -> MambaUserInputNode(node.position, node.powered.toInt() * 15)
                    else -> null
                }
                if (mambaNode == null) continue
                mambaGraph.nodes.add(mambaNode)
                rsIrNodeToMamba[node] = mambaNode
            }

            // Fill node inputs
            for ((rsIrNode, mambaNode) in rsIrNodeToMamba) {
                for (input in rsIrNode.getInputs()) {
                    val mambaInputNode = rsIrNodeToMamba[input.node] ?: continue
                    mambaNode.inputs.add(MambaInput(mambaInputNode, input.dist, input.linkType == BackwardLinkType.SIDE))
                }
            }

            // Fill positions
            for ((rsIrNode, mambaNode) in rsIrNodeToMamba) {
                if (rsIrNode.position != null) {
                    positionedNodes[rsIrNode.position] = mambaNode
                }
            }

            // Fill input positions
            for ((nodePos, node) in positionedNodes) {
                if (node is MambaUserInputNode) {
                    positionedUserInputNodes[nodePos] = node
                }
            }

            return MambaGraphResult(
                mambaGraph,
                positionedNodes,
                positionedUserInputNodes,
            )
        }

    }

}