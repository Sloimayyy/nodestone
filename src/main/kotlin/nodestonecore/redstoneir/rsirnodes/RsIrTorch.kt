package com.sloimay.threadstonecore.redstoneir.rsirnodes

import com.sloimay.mcvolume.McVolume
import com.sloimay.smath.vectors.IVec3
import com.sloimay.threadstonecore.redstoneir.rsirnodes.RsIrNode

class RsIrTorch(
    parentVol: McVolume,
    position: IVec3,

    var lit: Boolean,
) : RsIrNode(parentVol, position) {

    override val ID: Int = 11

}