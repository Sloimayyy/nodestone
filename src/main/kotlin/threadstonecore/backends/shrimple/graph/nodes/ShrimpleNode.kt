package com.sloimay.threadstonecore.backends.shrimple.graph.nodes

import com.sloimay.threadstonecore.backends.shrimple.helpers.ShrimpleHelper.Companion.decomposeInt
import com.sloimay.threadstonecore.backends.shrimple.helpers.ShrimpleHelper.Companion.getBitField
import com.sloimay.threadstonecore.backends.shrimple.helpers.ShrimpleHelper.Companion.setBitField
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

class ShrimpleNodeIntRepr(
    var parity: Boolean,
    var type: Int,
    var constantData: Int,
    var evenTicksData: Int,
    var oddTicksData: Int
) {
    companion object {
        fun fromInt(i: Int): ShrimpleNodeIntRepr {
            val decomposed = decomposeInt(i, 1, 4, 3, 8, 8, 8)
            return ShrimpleNodeIntRepr(
                decomposed[0] == 1,
                decomposed[1],
                decomposed[3],
                decomposed[4],
                decomposed[5],
            )
        }

        fun getIntReprFromBits(
            parity: Boolean,
            type: Int,
            constantData: Int,
            evenTicksData: Int,
            oddTicksData: Int
        ): Int {
            return ShrimpleNodeIntRepr(
                parity, type, constantData, evenTicksData, oddTicksData
            ).toInt()
        }

        fun getIntParityPointedDataBits(i: Int) = getBitField(i, 16 + getIntCurrentParityBit(i)*8, 8)
        fun getIntNotParityPointedDataBits(i: Int) = getBitField(i, 16 + (1 - getIntCurrentParityBit(i))*8, 8)
        fun getIntCurrentParityBit(i: Int) = getBitField(i, 0, 1)
        fun getIntCurrentType(i: Int) = getBitField(i, 1, 4)
        fun getIntConstantData(i: Int) = getBitField(i, 8, 8)

        fun setIntParity(i: Int, newParity: Int): Int {
            return setBitField(i, newParity, 0, 1)
        }

        /**
         * Sets the data bits of the current data bits the parity bit is pointing to
         */
        fun setIntCurrentDataBits(i: Int, dataBits: Int): Int {
            return setIntDataBitsAtParity(i, dataBits, getIntCurrentParityBit(i))
        }

        fun setIntNextDataBits(i: Int, dataBits: Int): Int {
            return setIntDataBitsAtParity(i, dataBits, 1 - getIntCurrentParityBit(i))
        }

        fun setIntDataBitsAtParity(i: Int, dataBits: Int, parity: Int): Int {
            return setBitField(i, dataBits, 16 + parity*8, 8)
        }

        fun getNextNodeIntWithDynDataBits(currNodeInt: Int, nextDynDataBits: Int): Int {
            val parity = getIntCurrentParityBit(currNodeInt)
            val currNodeIntWithNewParityBit = setIntParity(currNodeInt, 1 - parity)
            // Set current because we've already inverted the polarity of the parity
            return setIntCurrentDataBits(currNodeIntWithNewParityBit, nextDynDataBits)
        }
    }

    fun toInt(): Int {
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

abstract class ShrimpleNode(val pos: IVec3?) {

    abstract val type: ShrimpleNodeType

    val inputs = mutableListOf<ShrimpleInputEdge>()
    val outputs = mutableListOf<ShrimpleOutputEdge>()
    var idxInArray: Int? = null

    abstract fun getIntRepr(): Int

}