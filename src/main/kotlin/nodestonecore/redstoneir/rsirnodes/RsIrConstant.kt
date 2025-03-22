package com.sloimay.threadstonecore.redstoneir.rsirnodes

import com.sloimay.mcvolume.McVolume
import com.sloimay.smath.vectors.IVec3


class RsIrConstant(
    parentVol: McVolume,
    position: IVec3?,

    var signalStrength: Int,
) : RsIrNode(parentVol, position) {
    override val ID: Int = 1

}