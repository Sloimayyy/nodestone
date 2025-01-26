package com.sloimay.threadstonecore.redstoneir.rsirnodes

import me.sloimay.mcvolume.McVolume
import me.sloimay.smath.vectors.IVec3

class RsIrRepeater(
    parentVol: McVolume,
    position: IVec3,

    val realDelay: Int,

    var powered: Boolean,
    var locked: Boolean,
) : RsIrNode(parentVol, position) {





}