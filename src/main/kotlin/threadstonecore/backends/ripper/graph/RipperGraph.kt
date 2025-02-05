package com.sloimay.threadstonecore.backends.ripper.graph

import com.sloimay.threadstonecore.backends.ripper.graph.nodes.RipperNode
import com.sloimay.threadstonecore.backends.ripper.graph.nodes.RipperOutputEdge
import com.sloimay.threadstonecore.backends.ripper.helpers.RipperHelper.Companion.toBitsInt
import com.sloimay.threadstonecore.backends.ripper.helpers.int
import me.sloimay.smath.clamp


class RipperGraphSerResult(val nodesArray: ShortArray,
                           val edgePointerArray: IntArray,
                           val edgeArray: IntArray,
                           val timestampArray: LongArray)


class RipperGraph {

    val nodes = mutableListOf<RipperNode>()

    fun serialize(): RipperGraphSerResult {
        for ((idx, n) in nodes.withIndex()) {
            n.idxInArray = idx
        }

        val nodesArray = MutableList<Short>(nodes.size * 2) { 0 }
        val edgePointerArray = mutableListOf<Int>()
        val edgeArray = mutableListOf<Int>()

        val outputEdges = hashMapOf<RipperNode, MutableList<RipperOutputEdge>>()
        // Get the node outputs
        for (n in nodes) outputEdges[n] = mutableListOf()
        for (n in nodes) {
            for (i in n.inputs) {
                outputEdges[i.node]!!.add(RipperOutputEdge(n, i.dist, false))
            }
        }

        // Serialize nodes
        for (n in nodes) {
            val nodeTypeBits = n.ID.int
            val dataBits = n.getDataBits()
            val nodeState = toBitsInt(
                nodeTypeBits to 4,
                dataBits to 12,
            ).toShort()
            nodesArray[n.idxInArray] = nodeState
            nodesArray[n.idxInArray + nodes.size] = nodeState
        }

        // Serialize edges
        for (n in nodes) {
            // Serialize inputs
            val inputStartIdx = edgeArray.size
            for (i in n.inputs) {
                val inputEdgeInt = toBitsInt(
                    i.dist.clamp(0, 15) to 4,
                    i.side.int to 1,
                    i.node.idxInArray to 27,
                )
                edgeArray.add(inputEdgeInt)
            }

            // Serialize outputs
            val outputStartIdx = edgeArray.size
            for (o in outputEdges[n]!!) {
                val outputEdgeInt = toBitsInt(
                    o.dist.clamp(0, 15) to 4,
                    o.side.int to 1,
                    o.node.idxInArray to 27,
                )
                edgeArray.add(outputEdgeInt)
            }

            // Serialize the counts int
            val countsInt = toBitsInt(
                n.inputs.size to 16,
                outputEdges[n]!!.size to 16,
            )

            edgePointerArray.addAll(listOf(
                inputStartIdx,
                outputStartIdx,
                countsInt,
            ))
        }

        val timestampArray = LongArray(nodes.size * 2) { -1 }
        return RipperGraphSerResult( nodesArray.toShortArray(), edgePointerArray.toIntArray(), edgeArray.toIntArray(), timestampArray )
    }

}