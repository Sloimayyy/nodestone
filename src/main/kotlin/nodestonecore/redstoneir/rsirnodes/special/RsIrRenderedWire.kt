package com.sloimay.nodestonecore.redstoneir.rsirnodes.special

import com.sloimay.nodestonecore.redstoneir.rsirnodes.RsIrNode
import com.sloimay.mcvolume.McVolume
import com.sloimay.smath.vectors.IVec3

/*
class RenderedRsWireInput(val node: RsNode, val dist: Int)
class RenderedRsWire(val inputs: MutableList<RenderedRsWireInput>)
 */


class RsIrRenderedWireInput(var node: RsIrNode, var dist: Int)
class RsIrRenderedWire(
    val parentVol: McVolume,
    val pos: IVec3,
    val inputs: MutableList<RsIrRenderedWireInput>
) {
    //fun getInputs(): MutableList<RsIrRenderedWireInput> = inputs
    fun addInput(i: RsIrRenderedWireInput) {
        this.inputs.add(i)
    }
}