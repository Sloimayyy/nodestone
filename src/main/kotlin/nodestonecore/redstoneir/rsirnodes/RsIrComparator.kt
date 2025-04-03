package com.sloimay.nodestonecore.redstoneir.rsirnodes

import com.sloimay.mcvolume.McVolume
import com.sloimay.smath.vectors.IVec3

enum class RsIrCompMode {
    COMPARE,
    SUBTRACT;

    companion object {
        fun fromProp(prop: String): RsIrCompMode {
            return when (prop) {
                "compare" -> COMPARE
                "subtract" -> SUBTRACT
                else -> throw Exception("Inputted prop '$prop' isn't a valid comparator mode.")
            }
        }
    }
}

class RsIrComparator(
    parentVol: McVolume,
    position: IVec3,

    val compMode: RsIrCompMode,
    val farInputSs: Int, // -1 if no far input

    var outputSs: Int,
) : RsIrNode(parentVol, position) {

    override val ID: Int = 0

    fun hasFarInput() = farInputSs != -1

}