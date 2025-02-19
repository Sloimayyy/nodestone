package com.sloimay.threadstonecore.backends.shrimple.graph.nodes

import com.sloimay.threadstonecore.backends.ripper.graph.nodes.RipperInputEdge
import com.sloimay.threadstonecore.backends.ripper.graph.nodes.RipperNodeType
import com.sloimay.threadstonecore.backends.shrimple.helpers.ShrimpleHelper
import com.sloimay.threadstonecore.backends.shrimple.helpers.ShrimpleHelper.Companion.toBitsInt
import com.sloimay.threadstonecore.backends.shrimple.helpers.int
import me.sloimay.smath.clamp
import me.sloimay.smath.vectors.IVec3

enum class ShrimpleNodeType(val int: Int) {
    CONSTANT(0),
    USER_INPUT(1),
    COMPARATOR(2),
    LAMP(3),
    TORCH(4),
    REPEATER(5),
}

interface ShrimpleEdge {
    fun serialize(): Int
}

data class ShrimpleInputEdge(val node: ShrimpleNode, val dist: Int, val isSide: Boolean) : ShrimpleEdge {
    override fun serialize(): Int {
        return toBitsInt(
            dist.clamp(0, 15) to 4,
            isSide.int to 1,
            node.idxInArray!! to 27,
        )
    }
}

data class ShrimpleOutputEdge(val node: ShrimpleNode, val dist: Int) : ShrimpleEdge {
    override fun serialize(): Int {
        return toBitsInt(
            dist.clamp(0, 15) to 4,
            false.int to 1,
            node.idxInArray!! to 27,
        )
    }
}

abstract class ShrimpleNode(val pos: IVec3?) {

    companion object {
        fun getIntReprFromBits(
            parity: Boolean,
            type: Int,
            constantData: Int,
            evenTicksData: Int,
            oddTicksData: Int
        ): Int {
            return toBitsInt(
                parity.int to 1,
                type to 4,
                0 to 3, // unused bits
                constantData to 8,
                evenTicksData to 8,
                oddTicksData to 8,
            )
        }
    }

    abstract val type: ShrimpleNodeType

    val inputs = mutableListOf<ShrimpleInputEdge>()
    val outputs = mutableListOf<ShrimpleOutputEdge>()
    var idxInArray: Int? = null

    abstract fun getIntRepr(): Int
}