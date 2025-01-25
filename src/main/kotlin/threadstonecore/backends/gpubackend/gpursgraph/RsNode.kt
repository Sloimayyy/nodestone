package com.sloimay.threadstonecore.backends.gpubackend.gpursgraph

import me.sloimay.mcvolume.block.BlockState
import com.sloimay.threadstonecore.backends.gpubackend.helpers.RsGraphUtils.Companion.toBitsInt
import com.sloimay.threadstonecore.backends.gpubackend.helpers.toInt
import kotlin.math.min



abstract class RsNode {

    internal val inputs: MutableList<RsInput> = mutableListOf()
    internal var arrayIdx: Int = -1
    internal var parentGraphId: Int = -1
    fun addInput(node: RsNode, dist: Int, side: Boolean = false) {
        this.inputs.add(RsInput(node, dist, side))
    }

    abstract fun serialize(ints: MutableList<Int>)
    abstract fun deserializeIntoItself(data: Int)

    // Used for redstone rendering
    abstract fun getSs(): Int

    abstract fun changeBlockState(blockState: BlockState): BlockState

    fun serializeInputs(ints: MutableList<Int>) {
        for (input in this.inputs) {
            val inputInt = toBitsInt(
                input.dist to INPUT_REDSTONE_DIST_BIT_COUNT, // Redstone distance
                input.side.toInt() to 1, // Whether it's a side input
                0 to 27, // Pointer to the input. Backpatch later
            )
            ints.add(inputInt)
        }
    }

    fun removeDupInputs() {

        //println("Deduping begins ...")

        // # Remove inputs that appear twice or more, but also fold them into a single
        // # input with the lowest redstone distance

        val iterInputList: MutableList<RsInput?> = MutableList(this.inputs.size) { this.inputs[it] }
        val newInputList = mutableListOf<RsInput>()

        for (idx in iterInputList.indices) {
            val input = iterInputList[idx] ?: continue
            iterInputList[idx] = null
            var lowestDist = input.dist
            for (idx2 in iterInputList.indices) {
                val input2 = iterInputList[idx2] ?: continue
                if (input2.side != input.side) continue
                if (input2.node !== input.node) continue
                // We found an input that's literally the same as the base one but the distance is different
                //println("FOUND MATCHING")
                iterInputList[idx2] = null
                lowestDist = min(lowestDist, input2.dist)
            }
            newInputList.add(RsInput(input.node, lowestDist, input.side))
        }

        // Replace old input list with new one
        this.inputs.clear()
        this.inputs.addAll(newInputList)
    }
}