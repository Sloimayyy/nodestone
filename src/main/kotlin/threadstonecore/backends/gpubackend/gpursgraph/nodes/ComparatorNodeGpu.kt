package com.sloimay.threadstonecore.backends.gpubackend.gpursgraph.nodes

import com.sloimay.threadstonecore.backends.gpubackend.gpursgraph.*
import me.sloimay.mcvolume.block.BlockState
import com.sloimay.threadstonecore.backends.gpubackend.helpers.RsGraphUtils.Companion.decomposeInt
import com.sloimay.threadstonecore.backends.gpubackend.helpers.RsGraphUtils.Companion.toBitsInt
import com.sloimay.threadstonecore.backends.gpubackend.helpers.toInt

const val COMPARATOR_ID = 3


class ComparatorNodeGpu (var outputSs: Int,

    // Not meant to change
                         val hasFarInput: Boolean,
                         val farInputSs: Int,
                         val mode: Boolean) : GpuRsNode() {

    override fun serialize(ints: MutableList<Int>) {
        // Component int
        val idAndData = toBitsInt(
            COMPARATOR_ID to NODE_TYPE_BIT_COUNT, // id
            this.outputSs to 4,
            this.hasFarInput.toInt() to 1,
            this.farInputSs to 4,
            this.mode.toInt() to 1,
        )
        val componentInt = toBitsInt(
            idAndData to NODE_INPUT_COUNT_SHIFT,
            this.inputs.size to 16
        )
        ints.add(componentInt)

        // The inputs
        this.serializeInputs(ints)
    }

    override fun deserializeIntoItself(data: Int) {
        val nodeId = data and NODE_TYPE_BIT_MASK
        if (nodeId != COMPARATOR_ID) {
            throw Exception("Comparator node didn't receive correct node data")
        }
        val nodeData = (data and NODE_DATA_BIT_MASK) ushr NODE_TYPE_BIT_COUNT

        val (outputSs, hasFarInput, farInputSs, mode) = decomposeInt(nodeData, 4, 1, 4, 1)
        this.outputSs = outputSs
    }

    override fun changeBlockState(blockState: BlockState): BlockState {
        val mut = blockState.toMutable()
        mut.setProp("powered", if (this.outputSs > 0) "true" else "false")
        return mut.toImmutable()
    }

    override fun getSs() = this.outputSs

    override fun toString(): String {
        return "ComparatorNode: oSs:${outputSs}, hasFarInput:$hasFarInput, farInputSs: $farInputSs, mode: $mode"
    }

}