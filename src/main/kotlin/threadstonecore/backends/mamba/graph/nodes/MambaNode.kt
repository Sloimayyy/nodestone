package com.sloimay.threadstonecore.backends.mamba.graph.nodes

import com.sloimay.threadstonecore.backends.mamba.graph.MAMBA_DATA_BIT_LEN
import com.sloimay.threadstonecore.backends.mamba.graph.MAMBA_DO_UPDATE_BIT_LEN
import com.sloimay.threadstonecore.backends.mamba.graph.MAMBA_TYPE_BIT_LEN
import me.sloimay.smath.vectors.IVec3


enum class MambaNodeType(val int: Int) {
    CONSTANT(0),
    USER_INPUT(1),
    COMPARATOR(2),
    LAMP(3),
    TORCH(4),
    REPEATER(5),
}


abstract class MambaNode(val pos: IVec3?) {

    abstract val ID: MambaNodeType

    internal val inputs: MutableList<MambaInput> = mutableListOf()
    var idxInArray: Int = -1
    var idxInSerializedArray: Int = -1


    abstract fun getDataBits(): Int


    companion object {
        class IntRepr {
            companion object {

                fun getDataBitsAtParity(nodeInt: Int, parity: Int): Int {
                    val shift = MAMBA_TYPE_BIT_LEN + MAMBA_DO_UPDATE_BIT_LEN + (parity * (MAMBA_DO_UPDATE_BIT_LEN + MAMBA_DATA_BIT_LEN))
                    val mask = (1 shl MAMBA_DATA_BIT_LEN) - 1
                    return (nodeInt ushr shift) and mask
                }

                fun getTypeBits(nodeInt: Int): Int {
                    val mask = (1 shl MAMBA_TYPE_BIT_LEN) - 1
                    return nodeInt and mask
                }

            }
        }
    }


}