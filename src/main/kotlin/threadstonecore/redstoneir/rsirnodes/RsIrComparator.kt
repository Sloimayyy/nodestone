package com.sloimay.threadstonecore.redstoneir.rsirnodes

import me.sloimay.mcvolume.McVolume
import me.sloimay.smath.vectors.IVec3

enum class ComparatorMode {
    COMPARE,
    SUBTRACT;

    companion object {
        fun fromProp(prop: String): ComparatorMode {
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

    val compMode: ComparatorMode,
    val farInputSs: Int, // -1 if no far input

    var outputSs: Int,
) : RsIrNode(parentVol, position) {

    fun hasFarInput() = farInputSs != -1

}