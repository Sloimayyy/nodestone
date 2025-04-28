package com.sloimay.nodestonecore.simulation.backends.ripper.graph.nodes

import com.sloimay.nodestonecore.backends.ripper.graph.nodes.RipperNode

data class RipperOutputEdge(val node: RipperNode, val dist: Int, val side: Boolean)