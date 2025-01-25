package com.sloimay.threadstonecore.backends.gpubackend.gpursgraph.nodes

import com.sloimay.threadstonecore.backends.gpubackend.gpursgraph.NODE_INPUT_COUNT_SHIFT
import com.sloimay.threadstonecore.backends.gpubackend.gpursgraph.NODE_TYPE_BIT_COUNT
import com.sloimay.threadstonecore.backends.gpubackend.gpursgraph.NODE_TYPE_BIT_MASK
import com.sloimay.threadstonecore.backends.gpubackend.gpursgraph.RsNode
import me.sloimay.mcvolume.block.BlockState
import com.sloimay.threadstonecore.backends.gpubackend.helpers.RsGraphUtils.Companion.toBitsInt

const val CONSTANT_ID = 0


class ConstantNode(val power: Int) : RsNode() {

    override fun serialize(ints: MutableList<Int>) {
        // Component int
        val idAndData = toBitsInt(
            CONSTANT_ID to NODE_TYPE_BIT_COUNT, // id
            this.power to 4,
        )
        val componentInt = toBitsInt(
            idAndData to NODE_INPUT_COUNT_SHIFT,
            this.inputs.size to 16
        )
        ints.add(componentInt)

        // The inputs (even though theres none)
        this.serializeInputs(ints)
    }

    override fun deserializeIntoItself(data: Int) {
        val nodeId = data and NODE_TYPE_BIT_MASK
        if (nodeId != CONSTANT_ID) {
            throw Exception("Constant node didn't receive correct node data")
        }
    }

    override fun changeBlockState(blockState: BlockState): BlockState {
        return blockState
    }

    override fun getSs() = this.power

    override fun toString(): String {
        return "ConstantNode: ss: $power"
    }

}