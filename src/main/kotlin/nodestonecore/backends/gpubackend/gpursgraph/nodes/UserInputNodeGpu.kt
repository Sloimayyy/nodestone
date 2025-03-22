package com.sloimay.threadstonecore.backends.gpubackend.gpursgraph.nodes

import com.sloimay.threadstonecore.backends.gpubackend.gpursgraph.*
import me.sloimay.mcvolume.block.BlockState
import com.sloimay.threadstonecore.backends.gpubackend.helpers.RsGraphUtils.Companion.decomposeInt
import com.sloimay.threadstonecore.backends.gpubackend.helpers.RsGraphUtils.Companion.toBitString
import com.sloimay.threadstonecore.backends.gpubackend.helpers.RsGraphUtils.Companion.toBitsInt

const val USER_INPUT_NODE_ID = 5


class UserInputNodeGpu(var power: Int) : GpuRsNode() {

    override fun serialize(ints: MutableList<Int>) {
        // Component int
        val idAndData = toBitsInt(
            USER_INPUT_NODE_ID to NODE_TYPE_BIT_COUNT, // id
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
        if (nodeId != USER_INPUT_NODE_ID) {
            throw Exception("UserInput node didn't receive correct node data received: ${toBitString(data)}")
        }

        val nodeData = (data and NODE_DATA_BIT_MASK) ushr NODE_TYPE_BIT_COUNT

        val (power) = decomposeInt(nodeData, 4)
        this.power = power
        //println("Set visualisation user input node to power: ${this.power}")
    }

    override fun changeBlockState(blockState: BlockState): BlockState {
        val bsMut = blockState.toMutable()
        when (blockState.fullName) {
            "minecraft:lever",
            "minecraft:stone_button",
            "minecraft:stone_pressure_plate" -> {
                bsMut.setProp("powered", if (this.power > 0) "true" else "false")
            }
        }
        return bsMut.toImmutable()
    }

    override fun getSs() = this.power

    override fun toString(): String {
        return "UserInputNode: ss: $power"
    }

}