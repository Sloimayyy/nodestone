package com.sloimay.nodestonecore.redstoneir.rsirnodes

import com.sloimay.mcvolume.McVolume
import com.sloimay.nodestonecore.redstoneir.rsirnodes.RsIrNode
import com.sloimay.smath.vectors.IVec3


class RsIrRepeater(
    parentVol: McVolume,
    position: IVec3,

    val realDelay: Int,

    var powered: Boolean,
    var locked: Boolean,
) : RsIrNode(parentVol, position) {


    override val ID: Int = 10



}