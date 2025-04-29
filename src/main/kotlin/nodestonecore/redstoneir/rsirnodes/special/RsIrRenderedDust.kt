package com.sloimay.nodestonecore.redstoneir.rsirnodes.special

import com.sloimay.nodestonecore.redstoneir.rsirnodes.RsIrNode
import com.sloimay.mcvolume.McVolume
import com.sloimay.smath.vectors.IVec3

/*
class RenderedRsWireInput(val node: RsNode, val dist: Int)
class RenderedRsWire(val inputs: MutableList<RenderedRsWireInput>)
 */


class RsIrRenderedDustInput(var node: RsIrNode, var dist: Int)
class RsIrRenderedDust(
    val parentVol: McVolume,
    val pos: IVec3,
    val inputs: MutableList<RsIrRenderedDustInput>,
    val startSs: Int,
) {
    //fun getInputs(): MutableList<RsIrRenderedWireInput> = inputs
    fun addInput(i: RsIrRenderedDustInput) {
        this.inputs.add(i)
    }
}