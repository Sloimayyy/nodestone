package com.sloimay.threadstonecore.backends.ripper

import com.sloimay.threadstonecore.backends.RedstoneSimBackend
import com.sloimay.threadstonecore.backends.ripper.graph.RipperGraph
import com.sloimay.threadstonecore.backends.ripper.graph.nodes.RipperUserInputNode
import com.sloimay.threadstonecore.redstoneir.RedstoneBuildIR
import com.sloimay.threadstonecore.redstoneir.from.fromVolume
import me.sloimay.mcvolume.IntBoundary
import me.sloimay.mcvolume.McVolume
import me.sloimay.mcvolume.block.BlockState
import me.sloimay.smath.vectors.IVec3
import java.util.concurrent.CyclicBarrier
import kotlin.concurrent.thread

private abstract class RipperScheduledAction
private class RipperScheduledUserInput(val inputNode: RipperUserInputNode, val powerToSet: Int) : RipperScheduledAction()


class RipperBackend private constructor(
    volume: McVolume,
    simBounds: IntBoundary,

    val threadCount: Int,
    val graph: RipperGraph,

    val dualGraphBufferStart: IntArray,
    val dualGraphBuffer: IntArray,

    val timestampArray: LongArray,
    val edgePointerArray: IntArray,
    val edgeArray: IntArray,

    val nodesAddedBitmap: IntArray,

    val updateDynArray: IntArray,

) : RedstoneSimBackend(volume, simBounds) {

    var currentTick: Long = 0L
    var updateDynArrayLen: Int = 0
    private val userInputScheduler: HashMap<Int, MutableList<RipperScheduledUserInput>> = hashMapOf()
    override fun processScheduledUserInputsNextTick() {
        // not implemented but doesnt matter lol
        // TODO: implement it propermy
    }


    companion object {

        fun new(
            vol: McVolume,
            simBounds: IntBoundary,
            threadCount: Int,
        ): RipperBackend {
            val irGraph = RedstoneBuildIR.fromVolume(vol)
            val graph = RipperGraph.fromIR(irGraph)

            val serResult = graph.serialize()
            val edgePointerArray = serResult.edgePointerArray
            val edgeArray = serResult.edgeArray
            val timestampArray = serResult.timestampArray
            val dualGraphBufferStart = serResult.nodesArray
            val dualGraphBuffer = IntArray(dualGraphBufferStart.size) { dualGraphBufferStart[it] }

            val updateDynArray = IntArray(graph.nodes.size * 2) { 0 }

            /**
             * TODO: optimisation? pad the array to a multiple of 4 (or 8) so in the clearing
             *       you could get the compiler to vectorize the mem sets
             */
            val nodesAddedBitmap = IntArray((graph.nodes.size + 31) / 32) { 0 }


            return RipperBackend(
                volume = vol,
                simBounds = simBounds,

                threadCount,

                graph,
                dualGraphBufferStart,
                dualGraphBuffer,

                timestampArray,
                edgePointerArray,
                edgeArray,

                nodesAddedBitmap,
                updateDynArray,
            )
        }

    }


    override fun tickWhile(pred: () -> Boolean) {
        var predResult = false
        fun setupTick() {
            predResult = pred()
            this.nodesAddedBitmap.fill(0)
        }

        val barrier = CyclicBarrier(threadCount) {
            setupTick()
        }

        val threads = (0 until threadCount).map {
            thread(start = false) {
                val threadIdx = it


                while (predResult == true) {
                    TODO("tick")

                    barrier.await()
                }
            }
        }

        setupTick()
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }

    override fun updateRepr(
        updateVolume: Boolean,
        onlyNecessaryVisualUpdates: Boolean,
        renderCallback: (renderPos: IVec3, newBlockState: BlockState) -> Unit
    ) {
        TODO("Not yet implemented")
    }

    override fun getInputNodePositions(): Set<IVec3> {
        TODO("Not yet implemented")
    }

    override fun scheduleButtonPress(ticksFromNow: Int, pressLength: Int, inputNodePos: IVec3) {
        TODO("Not yet implemented")
    }

    override fun scheduleUserInputChange(ticksFromNow: Int, inputNodePos: IVec3, power: Int) {
        TODO("Not yet implemented")
    }


}