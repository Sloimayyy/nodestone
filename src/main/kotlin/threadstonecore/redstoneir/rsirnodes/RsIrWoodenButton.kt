package com.sloimay.threadstonecore.redstoneir.rsirnodes

import com.sloimay.threadstonecore.redstoneir.rsirnodes.RsIrNode
import me.sloimay.mcvolume.McVolume
import me.sloimay.smath.vectors.IVec3



class RsIrWoodenButton(
    parentVol: McVolume,
    position: IVec3,

    var powered: Boolean,
) : RsIrInputNode(parentVol, position) {

    override val ID: Int = 6


}