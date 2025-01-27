package com.sloimay.threadstonecore.redstoneir

import com.sloimay.threadstonecore.redstoneir.from.fromVolume
import com.sloimay.threadstonecore.redstoneir.rsirnodes.*
import com.sloimay.threadstonecore.redstoneir.rsirnodes.special.RsIrRenderedWire
import me.sloimay.mcvolume.McVolume


class RedstoneBuildIR internal constructor(
    internal val vol: McVolume
) {
    companion object {
        fun new(vol: McVolume): RedstoneBuildIR {
            return RedstoneBuildIR.fromVolume(vol)
        }
    }

    internal val inputNodes: MutableList<RsIrInputNode> = mutableListOf()
    internal val outputNodes: MutableList<RsIrNode> = mutableListOf()
    internal val nodes: MutableList<RsIrNode> = mutableListOf()
    internal val renderedWires: MutableList<RsIrRenderedWire> = mutableListOf()

    fun addNode(node: RsIrNode) {
        this.nodes.add(node)
    }
    fun addRenderedRsWire(renderedRsWire: RsIrRenderedWire) {
        this.renderedWires.add(renderedRsWire)
    }

    fun getNodes(): List<RsIrNode> = this.nodes
    fun getInputNodes(): List<RsIrInputNode> = this.inputNodes
    fun getOutputNodes(): List<RsIrNode> = this.outputNodes
    fun getRenderedRsWires(): List<RsIrRenderedWire> = this.renderedWires

    /**
     * Method to call after adding all the nodes, clean up purposes
     */
    fun finalizeAllNodeAddition() {

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

    /**
     * Removes nodes that aren't an input or output of other nodes
     */
    private fun removeUndocumentedNodes() {
        val documented = hashSetOf<RsIrNode>()
        for (node in this.nodes) {
            for (i in node.getInputs()) {
                documented.add(i.node)
            }
            for (o in node.getOutputs()) {
                documented.add(o.node)
            }
        }
        val surviving = this.nodes.filter { node ->
            if (node is RsIrInputNode) return@filter true
            // Don't remove documented nodes
            if (node in documented) return@filter true
            return@filter false
        }.toMutableList()
        this.nodes.clear()
        this.nodes.addAll(surviving)
    }

    fun optimise() {
        removeUndocumentedNodes()
    }

}