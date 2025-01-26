package com.sloimay.threadstonecore.redstoneir

import com.sloimay.threadstonecore.redstoneir.from.fromVolume
import com.sloimay.threadstonecore.redstoneir.rsirnodes.*
import me.sloimay.mcvolume.McVolume
import me.sloimay.smath.vectors.IVec3


class RsIrGraph internal constructor(
    internal val vol: McVolume
) {
    companion object {
        fun new(vol: McVolume): RsIrGraph {
            return RsIrGraph.fromVolume(vol)
        }
    }

    internal val inputNodes: MutableList<RsIrInputNode> = mutableListOf()
    internal val outputNodes: MutableList<RsIrNode> = mutableListOf()
    internal val nodes: MutableList<RsIrNode> = mutableListOf()

    internal val nodePositions: HashMap<IVec3, RsIrNode> = hashMapOf()

    fun addNode(node: RsIrNode) {
        this.nodes.add(node)
    }
    fun getNodes(): List<RsIrNode> = this.nodes
    fun getInputNodes(): List<RsIrInputNode> = this.inputNodes
    fun getOutputNodes(): List<RsIrNode> = this.outputNodes

    fun getNodeAt(pos: IVec3) = nodePositions[pos]

    fun finalize() {
        // # Make node pos hashmap
        for (node in this.nodes) {
            this.nodePositions[node.position] = node
        }

        // # Document IO nodes
        for (node in this.nodes) {
            if (node is RsIrInputNode) {
                this.inputNodes.add(node)
            }
            if (node is RsIrLamp) {
                this.outputNodes.add(node)
            }
        }
    }

}