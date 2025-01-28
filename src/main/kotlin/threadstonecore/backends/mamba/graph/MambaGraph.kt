package com.sloimay.threadstonecore.backends.mamba.graph

import com.sloimay.threadstonecore.backends.mamba.graph.nodes.MambaNode

class MambaGraph {

    companion object {}

    val nodes: MutableList<MambaNode> = mutableListOf()


    fun serialize(): Pair<IntArray, IntArray> {
        val nodesArray = mutableListOf<Int>()
        val ioArray = mutableListOf<Int>()



    }


}