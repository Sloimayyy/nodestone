package com.sloimay.nodestonecore.simulation.initialisers

import com.sloimay.mcvolume.McVolume
import com.sloimay.nodestonecore.simulation.SimBackend
import com.sloimay.nodestonecore.simulation.SimInitializer
import com.sloimay.nodestonecore.simulation.backends.shrimple.ShrimpleBackend
import com.sloimay.nodestonecore.simulation.initinterfaces.CompileFlagInitialized
import com.sloimay.nodestonecore.simulation.initinterfaces.AreaRepresentationInitialized
import com.sloimay.smath.geometry.boundary.IntBoundary

/**
 *
 */
class ShrimpleBackendInitializer()
    : SimInitializer(),
    AreaRepresentationInitialized,
    CompileFlagInitialized
{

    private var volume: McVolume? = null
    private var areaBounds: IntBoundary? = null
    private var compileFlags: List<String>? = null

    override fun withAreaRepresentation(mcVolume: McVolume, areaBounds: IntBoundary) {
        this.volume = mcVolume
        this.areaBounds = areaBounds
    }
    override fun withCompileFlags(flags: List<String>) {
        this.compileFlags = flags.toList()
    }

    override fun finishInit(): SimBackend {
        require(volume != null) { "Initialisation incomplete, no McVolume passed." }
        require(areaBounds != null) { "Initialisation incomplete, no Area Bounds passed." }
        val flags = compileFlags ?: listOf()

        return ShrimpleBackend.new(volume!!, volume!!.computeBuildBounds(), flags)
    }

}