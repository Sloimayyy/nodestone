package com.sloimay.threadstonecore.backends.gpubackend.gpursgraph

import com.sloimay.threadstonecore.backends.gpubackend.gpursgraph.nodes.GpuRsNode


const val NODE_DATA_BIT_COUNT = 12
const val NODE_TYPE_BIT_COUNT = 4
const val NODE_TYPE_BIT_MASK = (1 shl NODE_TYPE_BIT_COUNT) - 1
const val NODE_DATA_BIT_MASK = ((1 shl NODE_DATA_BIT_COUNT) - 1) shl NODE_TYPE_BIT_COUNT
const val NODE_INPUT_COUNT_SHIFT = NODE_DATA_BIT_COUNT + NODE_TYPE_BIT_COUNT

const val INPUT_REDSTONE_DIST_BIT_COUNT = 4
const val INPUT_SIDE_FLAG_BIT_COUNT = 1


/**
 *
 */
class GpuRsGraph {

    // DONT REMOVE LOL
    companion object {

    }

    internal val nodes: MutableList<GpuRsNode> = mutableListOf()

    fun addNode(node: GpuRsNode) {
        /*if (node.hasBeenAdded) {
            throw Exception("Cannot add node to graph as it was already added to another.")
        }*/
        //node.arrayIdx = nodes.size
        nodes.add(node)
    }

    fun serialize(): Pair<IntArray, IntArray> {

        // # Optimisations
        this.removeNodeDups()
        this.removeNodeDupInputs()

        // When optimisations are done, update indexes
        this.updateNodeIndexes()

        /*
        println(" ========== Graph after optimisations:")
        for (n in this.nodes) {
            println("node: ${n}")
            for (i in n.inputs) {
                println("   input: ${i}")
            }
        }
        */


        val out = mutableListOf<Int>()

        // Maps the indexes of the nodes to their *final* indexes in the out array
        val serializedIndexes = mutableListOf<Int>()

        // First serialize every node without final indexes in the out array
        for (node in nodes) {
            serializedIndexes.add(out.size)
            node.serialize(out)
        }

        // Now backpatch
        run {
            var i = 0
            var nodeIdx = 0
            while (i < out.size) {
                val node = this.nodes[nodeIdx]
                val nodeSerializedData = out[i]
                val inputCount = nodeSerializedData ushr (NODE_INPUT_COUNT_SHIFT)
                for (j in 1 until (inputCount+1)) {
                    // Backpatch
                    val inputInt = out[i + j]
                    val nodePointedToIdx = node.inputs[j - 1].node.arrayIdx
                    // Get where this node actually is in the serialized array
                    val nodePointedToInSerializedArr = serializedIndexes[nodePointedToIdx]
                    val finalInputInt = inputInt or (
                            nodePointedToInSerializedArr shl
                                    (INPUT_REDSTONE_DIST_BIT_COUNT + INPUT_SIDE_FLAG_BIT_COUNT)
                    )
                    out[i + j] = finalInputInt
                }
                i += inputCount + 1
                nodeIdx++
            }
        }

        return out.toIntArray() to serializedIndexes.toIntArray()
    }

    /**
     * Takes serialized graph data and deser it into itself
     */
    fun deserializeInto(serializedGraphData: IntArray,
                        nodeIndexes: IntArray,
                        nodeChangeArray: IntArray,
                        updateAllNodes: Boolean,
    ) {
        for (nodeGraphIdx in 0 until nodeIndexes.size) {
            // Don't deser if not needed
            val nodeSerIdx = nodeIndexes[nodeGraphIdx]
            if (!updateAllNodes) {
                if ((nodeChangeArray[nodeSerIdx] == 0)) {
                    continue
                }
            }

            val nodeIntData = serializedGraphData[nodeSerIdx]
            nodes[nodeGraphIdx].deserializeIntoItself(nodeIntData)
        }
    }

    internal fun removeNodeDups() {
        // Could do:
        // this.nodes = this.nodes.toHashSet().toList()
        // But it wouldn't retain order and maybe that's good to keep for cache hits
        // UPDATE: YEAH, IT IS LOL

        val alreadySeen = hashSetOf<GpuRsNode>()
        for (i in (this.nodes.size-1) downTo 0) {
            val node = this.nodes[i]
            if (alreadySeen.contains(node)) {
                this.nodes.removeAt(i)
            } else {
                alreadySeen.add(node)
            }
        }
    }

    internal fun updateNodeIndexes() {
        for ((idx, node) in this.nodes.withIndex()) {
            node.arrayIdx = idx
        }
    }

    internal fun removeNodeDupInputs() {
        for (node in this.nodes) {
            node.removeDupInputs()
        }
    }


}