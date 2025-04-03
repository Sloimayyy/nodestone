package com.sloimay.nodestonecore.backends.gpubackend.gpursgraph.nodes

import com.sloimay.nodestonecore.backends.gpubackend.gpursgraph.NODE_DATA_BIT_MASK
import com.sloimay.nodestonecore.backends.gpubackend.gpursgraph.NODE_INPUT_COUNT_SHIFT
import com.sloimay.nodestonecore.backends.gpubackend.gpursgraph.NODE_TYPE_BIT_COUNT
import com.sloimay.nodestonecore.backends.gpubackend.gpursgraph.NODE_TYPE_BIT_MASK
import com.sloimay.threadstonecore.backends.gpubackend.gpursgraph.*
import me.sloimay.mcvolume.block.BlockState
import com.sloimay.nodestonecore.backends.gpubackend.helpers.RsGraphUtils.Companion.decomposeInt
import com.sloimay.nodestonecore.backends.gpubackend.helpers.RsGraphUtils.Companion.toBitsInt
import com.sloimay.nodestonecore.backends.gpubackend.helpers.toInt

const val LAMP_ID = 4

class LampNodeGpu(var lit: Boolean = true) : GpuRsNode() {

    override fun serialize(ints: MutableList<Int>) {
        // Component int
        val idAndData = toBitsInt(
            _root_ide_package_.com.sloimay.nodestonecore.backends.gpubackend.gpursgraph.nodes.LAMP_ID to 4, // id
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
        if (nodeId != _root_ide_package_.com.sloimay.nodestonecore.backends.gpubackend.gpursgraph.nodes.LAMP_ID) {
            throw Exception("Redstone lamp node didn't receive correct node data")
        }
        val nodeData = (data and NODE_DATA_BIT_MASK) ushr NODE_TYPE_BIT_COUNT

        val (lit) = decomposeInt(nodeData, 1)
        this.lit = lit == 1
    }

    override fun changeBlockState(blockState: BlockState): BlockState {
        val mut = blockState.toMutable()
        mut.setProp("lit", if (lit) "true" else "false")
        //println("set lamp to bs: ${mut.toImmutable()}")
        return mut.toImmutable()
    }

    override fun getSs() = if (this.lit) 15 else 0

}