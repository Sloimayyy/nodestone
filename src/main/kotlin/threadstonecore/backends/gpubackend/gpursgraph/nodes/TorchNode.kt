package com.sloimay.threadstonecore.backends.gpubackend.gpursgraph.nodes

import com.sloimay.threadstonecore.backends.gpubackend.gpursgraph.*
import me.sloimay.mcvolume.block.BlockState
import com.sloimay.threadstonecore.backends.gpubackend.helpers.RsGraphUtils.Companion.decomposeInt
import com.sloimay.threadstonecore.backends.gpubackend.helpers.RsGraphUtils.Companion.toBitsInt
import com.sloimay.threadstonecore.backends.gpubackend.helpers.toInt

const val TORCH_ID = 2

class TorchNode(var lit: Boolean = true) : RsNode() {

    override fun serialize(ints: MutableList<Int>) {
        // Component int
        val idAndData = toBitsInt(
            TORCH_ID to 4, // id
            this.lit.toInt() to 1,
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
        if (nodeId != TORCH_ID) {
            throw Exception("Torch node didn't receive correct node data")
        }
        val nodeData = (data and NODE_DATA_BIT_MASK) ushr NODE_TYPE_BIT_COUNT

        val (lit) = decomposeInt(nodeData, 1)
        this.lit = lit == 1
    }

    override fun changeBlockState(blockState: BlockState): BlockState {
        val mut = blockState.toMutable()
        mut.setProp("lit", if (lit) "true" else "false")
        return mut.toImmutable()
    }

    override fun getSs() = if (this.lit) 15 else 0

}