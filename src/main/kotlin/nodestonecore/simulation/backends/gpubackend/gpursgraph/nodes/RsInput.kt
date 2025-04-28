package com.sloimay.nodestonecore.simulation.backends.gpubackend.gpursgraph.nodes

import com.sloimay.nodestonecore.backends.gpubackend.gpursgraph.nodes.GpuRsNode

data class RsInput(val node: GpuRsNode, val dist: Int, val side: Boolean)