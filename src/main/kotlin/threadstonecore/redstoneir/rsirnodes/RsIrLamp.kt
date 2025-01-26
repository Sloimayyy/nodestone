package com.sloimay.threadstonecore.redstoneir.rsirnodes

import me.sloimay.mcvolume.McVolume
import me.sloimay.smath.vectors.IVec3



class RsIrLamp(
    parentVol: McVolume,
    position: IVec3,

    var lit: Boolean,
) : RsIrNode(parentVol, position) {

}