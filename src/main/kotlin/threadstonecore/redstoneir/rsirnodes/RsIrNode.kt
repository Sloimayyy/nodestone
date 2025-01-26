package com.sloimay.threadstonecore.redstoneir.rsirnodes

import me.sloimay.mcvolume.McVolume
import me.sloimay.smath.vectors.IVec3


// Where a backwards link comes into
enum class BackwardLinkType {
    NORMAL,
    SIDE,
}

// Where a forward link comes out from
enum class ForwardLinkType {
    NORMAL,
}

/*
Keep their equalities as "Structural equalities". If you add a value that isn't in the main
constructor, make sure to add it to its .equals method. Data classes are nice but there definitely
are pitfalls to look out for.
 */
data class RsIrBackwardLink(val node: RsIrNode, val dist: Int, val linkType: BackwardLinkType)
data class RsIrForwardLink(val node: RsIrNode, val dist: Int, val linkType: ForwardLinkType)


abstract class RsIrNode(val parentVol: McVolume, val position: IVec3) {



    internal val inputs: MutableList<RsIrBackwardLink> = mutableListOf()
    internal val outputs: MutableList<RsIrForwardLink> = mutableListOf()

    fun getInputs(): List<RsIrBackwardLink> = inputs
    fun getOutputs(): List<RsIrForwardLink> = outputs

    fun addInput(link: RsIrBackwardLink) { this.inputs.add(link) }
    fun addOutput(link: RsIrForwardLink) { this.outputs.add(link) }

    fun removeDupLinks() {
        // Backward
        run {
            val h = hashSetOf<RsIrBackwardLink>()
            for (link in this.inputs) h.add(link)
            this.inputs.clear()
            for (link in h) this.inputs.add(link)
        }

        // Forward
        run {
            val h = hashSetOf<RsIrForwardLink>()
            for (link in this.outputs) h.add(link)
            this.outputs.clear()
            for (link in h) this.outputs.add(link)
        }
    }

}