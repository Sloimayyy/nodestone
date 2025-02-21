package com.sloimay.threadstonecore.redstoneir.rsirnodes

import com.sloimay.threadstonecore.redstoneir.rsirnodes.RsIrNode
import com.sloimay.mcvolume.McVolume
import com.sloimay.smath.vectors.IVec3



class RsIrWoodenPressurePlate(
    parentVol: McVolume,
    position: IVec3,

    var powered: Boolean,
) : RsIrInputNode(parentVol, position) {

    override val ID: Int = 5


}