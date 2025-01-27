package com.sloimay.threadstonecore.redstoneir

import com.sloimay.threadstonecore.backends.gpubackend.helpers.toInt
import com.sloimay.threadstonecore.redstoneir.from.fromVolume
import com.sloimay.threadstonecore.redstoneir.rsirnodes.*
import com.sloimay.threadstonecore.redstoneir.rsirnodes.special.RsIrRenderedWire
import me.sloimay.mcvolume.McVolume
import me.sloimay.smath.clamp


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

    // Array of *all* the nodes
    fun getNodes(): List<RsIrNode> = this.nodes
    // Array of nodes that are inputs
    fun getInputNodes(): List<RsIrInputNode> = this.inputNodes
    // Array of nodes that are outputs
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
     *
     */
    private fun mapNodeRefs(refMap: HashMap<RsIrNode, RsIrNode>) {
        for (nodeIdx in this.nodes.indices) {
            val oldNode = this.nodes[nodeIdx]
            val newNode = refMap[oldNode] ?: oldNode
            this.nodes[nodeIdx] = newNode
        }
        for (n in this.nodes) {
            for (i in n.getInputs()) {
                i.node = refMap[i.node] ?: i.node
            }
            for (o in n.getOutputs()) {
                o.node = refMap[o.node] ?: o.node
            }
        }
        for (rsw in this.renderedWires) {
            for (i in rsw.getInputs()) {
                i.node = refMap[i.node] ?: i.node
            }
        }
    }

    private fun dedupNodes() {
        val seen = hashSetOf<RsIrNode>()
        val newNodeArray = mutableListOf<RsIrNode>()
        for (n in this.nodes) {
            if (n !in seen) {
                newNodeArray.add(n)
                seen.add(n)
            }
        }
        this.nodes.clear()
        this.nodes.addAll(newNodeArray)
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
        for (node in this.renderedWires) {
            for (i in node.getInputs()) {
                documented.add(i.node)
            }
        }

        // IO is automatically documented
        for (n in this.inputNodes) documented.add(n)
        for (n in this.outputNodes) documented.add(n)

        // Filter to keep the order
        val surviving = this.nodes.filter { node ->
            // Don't remove documented nodes
            if (node in documented) return@filter true
            return@filter false
        }.toMutableList()
        this.nodes.clear()
        this.nodes.addAll(surviving)

        // Remove every node's inputs and outputs that reference an undocumented node
        /*for (n in this.nodes) {
            val newInputs = n.inputs.filter { i -> i.node in documented }
            n.inputs.clear();
            n.inputs.addAll(newInputs)

            val newOutputs = n.outputs.filter { i -> i.node in documented }
            n.outputs.clear()
            n.outputs.addAll(newOutputs)
        }*/
    }

    /**
     * Remove nodes that aren't connected to inputs or outputs
     */
    /*fun removeStrandedNodes() {
        val inputNodeHashSet = this.inputNodes.toHashSet()
        val outputNodeHashSet = this.outputNodes.toHashSet()

        val notStranded = hashSetOf<RsIrNode>()
        for (n in inputNodeHashSet) notStranded.add(n)
        for (n in outputNodeHashSet) notStranded.add(n)

        val searchStack = ArrayDeque<RsIrNode>()
        for (n in notStranded) searchStack.add(n)

        while (searchStack.isNotEmpty()) {
            val n = searchStack.removeFirst()
            notStranded.add(n)

            for (i in n.getInputs()) {
                val inputNode = i.node
                if (inputNode in notStranded)
            }
        }

    }*/

    /**
     * Fold constants of the same signal strength into a single node
     */
    private fun constantFolding() {
        val powerToConstantNode = hashMapOf<Int, RsIrConstant>()
        val oldConstToNewConst = hashMapOf<RsIrConstant, RsIrConstant>()

        for (n in this.nodes) {
            if (n !is RsIrConstant) continue
            if (n.signalStrength !in powerToConstantNode) {
                powerToConstantNode[n.signalStrength] = RsIrConstant(n.parentVol, null, n.signalStrength)
            }
            val newConst = powerToConstantNode[n.signalStrength]!!
            oldConstToNewConst[n] = newConst
        }


        // Change every input and output nodes that are constant with the new ones
        /*for (n in this.nodes) {
            for (i in n.getInputs()) {
                if (i.node in oldConstToNewConst) {
                    i.node = oldConstToNewConst[i.node]!!
                }
            }
            // Should not happen lol cuz you don't connect into a constant
            for (o in n.getOutputs()) {
                if (o.node in oldConstToNewConst) {
                    o.node = oldConstToNewConst[o.node]!!
                }
            }
        }*/

        this.mapNodeRefs(oldConstToNewConst as HashMap<RsIrNode, RsIrNode>)
        this.dedupNodes()

        this.nodes.addAll(powerToConstantNode.values)
    }


    private fun iterativeConstantTransforming() {
        val remaps = hashMapOf<RsIrNode, RsIrNode>()
        for (n in this.nodes) {
            if (n is RsIrComparator) {

                val sideInputs = mutableListOf<RsIrBackwardLink>()
                val backInputs = mutableListOf<RsIrBackwardLink>()
                for (i in n.getInputs()) {
                    when (i.linkType) {
                        BackwardLinkType.SIDE -> sideInputs.add(i)
                        BackwardLinkType.NORMAL -> backInputs.add(i)
                    }
                }

                val areAllBackInputsConstants = backInputs.all { it.node is RsIrConstant }
                val areAllSideInputsConstants = sideInputs.all { it.node is RsIrConstant }
                if (areAllBackInputsConstants && areAllSideInputsConstants) {
                    val maxBack = backInputs.maxOfOrNull { input ->
                        val node = input.node as RsIrConstant
                        val ssReceived = (node.signalStrength - input.dist).clamp(0, 15)
                        ssReceived
                    } ?: 0
                    val maxSide = sideInputs.maxOfOrNull { input ->
                        val node = input.node as RsIrConstant
                        val ssReceived = (node.signalStrength - input.dist).clamp(0, 15)
                        ssReceived
                    } ?: 0
                    val ssOutputted = if (n.compMode == ComparatorMode.COMPARE) {
                        maxBack * (maxSide <= maxBack).toInt()
                    } else {
                        (maxBack - maxSide).clamp(0, 15)
                    }
                    remaps[n] = RsIrConstant(n.parentVol, null, ssOutputted)
                }

                /*if (sideInputs.size == 0) {
                    if (n.getInputs().size == 1) {
                        val onlyInput = n.getInputs()[0]
                        val onlyInputNode = onlyInput.node
                        if (onlyInputNode is RsIrConstant) {
                            val ssReceived = (onlyInputNode.signalStrength - onlyInput.dist).clamp(0, 15)
                            remaps[n] = RsIrConstant(n.parentVol, null, ssReceived)
                        }
                    }
                }*/
            }
        }

        this.constantFolding()
        this.mapNodeRefs(remaps)
        this.dedupNodes()
    }

    fun sortNodes() {
        this.nodes.sortBy { it.ID }
    }

    fun optimise(ioOnly: Boolean = false) {
        // 224878

        println("====== Node count before optimisation: ${nodes.size}")
        constantFolding()
        removeUndocumentedNodes()
        //sortNodes()

        while (true) {
            val oldNodeCount = this.nodes.size

            iterativeConstantTransforming()



            val newNodeCount = this.nodes.size
            if (oldNodeCount == newNodeCount) break
        }



        if (ioOnly) {
            //removeStrandedNodes()
        }
        println("====== Node count after optimisation: ${nodes.size}")
    }

}