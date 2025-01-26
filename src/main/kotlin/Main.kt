package com.sloimay

import com.sloimay.threadstonecore.backends.gpubackend.gpursgraph.GpuRsGraph
import com.sloimay.threadstonecore.backends.gpubackend.gpursgraph.from.fromRsIrGraph
import com.sloimay.threadstonecore.redstoneir.RsIrGraph
import com.sloimay.threadstonecore.redstoneir.from.fromVolume
import me.sloimay.mcvolume.McVolume
import me.sloimay.smath.vectors.ivec3


/**
 *
 * TODO:
 *  - Switch GPU backend
 *  - Make the IR graph thingy and then make the GPU backend use it
 *
 */


fun main() {


    val v = McVolume.new(ivec3(0, 0, 0), ivec3(10, 10, 10))

    val w = v.getPaletteBlock("minecraft:redstone_wire")
    val s = v.getPaletteBlock("minecraft:stone")
    val pp = v.getPaletteBlock("minecraft:stone_pressure_plate")
    val t = v.getPaletteBlock("minecraft:redstone_torch")
    val ta = v.getPaletteBlock("minecraft:target")

    v.setBlock(ivec3(0, 0, 0), s)
    v.setBlock(ivec3(0, 1, 0), w)
    v.setBlock(ivec3(0, 1, 1), pp)
    v.setBlock(ivec3(0, 0, 1), s)
    v.setBlock(ivec3(1, 0, 0), s)
    v.setBlock(ivec3(1, 1, 0), ta)
    v.setBlock(ivec3(1, 2, 0), t)

    val g = GpuRsGraph.fromRsIrGraph(RsIrGraph.fromVolume(v))

    for (node in g.graph.nodes) {
        println(node)
        for (i in node.inputs) {
            println("   $i")
        }
    }

}