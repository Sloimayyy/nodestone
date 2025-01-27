package com.sloimay.threadstonecore.redstoneir.rsirnodes

import com.sloimay.threadstonecore.redstoneir.rsirnodes.RsIrNode
import me.sloimay.mcvolume.McVolume
import me.sloimay.smath.vectors.IVec3

class RsIrTorch(
    parentVol: McVolume,
    position: IVec3,

    var lit: Boolean,
) : RsIrNode(parentVol, position) {

    override val ID: Int = 11

}