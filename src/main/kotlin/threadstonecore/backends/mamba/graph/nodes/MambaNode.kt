package com.sloimay.threadstonecore.backends.mamba.graph.nodes

abstract class MambaNode {

    abstract val ID: Int

    internal val inputs: MutableList<MambaInput> = mutableListOf()




}