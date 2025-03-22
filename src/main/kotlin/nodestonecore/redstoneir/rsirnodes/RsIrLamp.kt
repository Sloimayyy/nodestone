package com.sloimay.threadstonecore.redstoneir.rsirnodes

import com.sloimay.mcvolume.McVolume
import com.sloimay.smath.vectors.IVec3


class RsIrLamp(
    parentVol: McVolume,
    position: IVec3,

    var lit: Boolean,
) : RsIrNode(parentVol, position) {
    override val ID: Int = 9

}