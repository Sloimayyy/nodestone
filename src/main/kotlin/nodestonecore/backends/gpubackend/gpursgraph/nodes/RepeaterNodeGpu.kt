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

const val REPEATER_ID = 1

class RepeaterNodeGpu(var locked: Boolean,
                      val delay: Int, // Actual delay on the repeater
                      var scheduler: Int) : GpuRsNode() {

    override fun serialize(ints: MutableList<Int>) {
        // Component int
        val idAndData = toBitsInt(
            REPEATER_ID to NODE_TYPE_BIT_COUNT, // id
            this.locked.toInt() to 1,
            (this.delay - 1) to 2,
            this.scheduler to 4,
        )
        val componentInt = toBitsInt(
            idAndData to NODE_INPUT_COUNT_SHIFT,
            this.inputs.size to 16
        )
        ints.add(componentInt)
        //println("Repeater serialize: ${toBitString(componentInt)}")

        // The inputs
        this.serializeInputs(ints)
    }

    override fun deserializeIntoItself(data: Int) {
        val nodeId = data and NODE_TYPE_BIT_MASK
        if (nodeId != REPEATER_ID) {
            throw Exception("Rep node didn't receive correct node data")
        }
        val nodeData = (data and NODE_DATA_BIT_MASK) ushr NODE_TYPE_BIT_COUNT

        val (locked, delay, scheduler) = decomposeInt(nodeData, 1, 2, 4)
        this.scheduler = scheduler
        this.locked = locked == 1
    }

    private fun isPowered() = ( (this.scheduler ushr (this.delay-1)) and 1 ) == 1

    override fun changeBlockState(blockState: BlockState): BlockState {
        val mut = blockState.toMutable()
        mut.setProp("powered", if (this.isPowered()) "true" else "false")
        mut.setProp("locked", if (locked) "true" else "false")
        return mut.toImmutable()
    }

    override fun getSs() = if (this.isPowered()) 15 else 0

}