package com.sloimay.threadstonecore.redstoneir.rsirnodes

import me.sloimay.mcvolume.McVolume
import me.sloimay.smath.vectors.IVec3



class RsIrConstant(
    parentVol: McVolume,
    position: IVec3,

    var signalStrength: Int,
) : RsIrNode(parentVol, position) {

}