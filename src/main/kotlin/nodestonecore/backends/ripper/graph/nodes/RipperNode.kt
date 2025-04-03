package com.sloimay.nodestonecore.backends.ripper.graph.nodes

import com.sloimay.smath.vectors.IVec3


enum class RipperNodeType(val int: Int) {
    CONSTANT(0),
    USER_INPUT(1),
    COMPARATOR(2),
    LAMP(3),
    TORCH(4),
    REPEATER(5),
}

abstract class RipperNode(val pos: IVec3?) {
    abstract val ID: RipperNodeType

    val inputs = mutableListOf<RipperInputEdge>()
    var idxInArray: Int = -1

    abstract fun getDataBits(): Int

}