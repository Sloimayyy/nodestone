package com.sloimay.threadstonecore.backends

import me.sloimay.mcvolume.IntBoundary
import me.sloimay.mcvolume.McVolume
import com.sloimay.threadstonecore.backends.gpubackend.gpursgraph.nodes.UserInputNode
import me.sloimay.smath.vectors.IVec3

abstract class RedstoneSimBackend(val volume: McVolume, simBounds: IntBoundary) {
    abstract fun tickWhile(pred: () -> Boolean)

    abstract fun render(updateVolume: Boolean = true, changeCallback: (changedPos: IVec3) -> Unit)

    abstract fun getInputNodeAt(nodePos: IVec3): UserInputNode?
    abstract fun scheduleButtonPress(ticksFromNow: Int, pressLength: Int, inputNode: UserInputNode)
    abstract fun scheduleUserInputChange(ticksFromNow: Int, inputNode: UserInputNode, power: Int)

}