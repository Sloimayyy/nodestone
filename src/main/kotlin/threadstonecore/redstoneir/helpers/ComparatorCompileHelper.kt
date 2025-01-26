package com.sloimay.threadstonecore.redstoneir.helpers

import me.sloimay.mcvolume.McVolume
import me.sloimay.mcvolume.block.BlockState
import me.sloimay.smath.clamp
import me.sloimay.smath.vectors.IVec3
import net.querz.nbt.tag.CompoundTag
import kotlin.math.floor

typealias GetCompReadFunc = (v: McVolume, pos: IVec3) -> Int

private val SsReadable = listOf<Pair<String, GetCompReadFunc>>(
    "minecraft:composter" to
            { v, p -> v.getBlock(p).state.getProp("level").orElse("0").toInt() },
    "minecraft:barrel" to fun(v, p): Int {
                val tileData = v.getTileData(p) ?: return 0
                return ComparatorCompileHelper.getContainerPower(tileData, 27)
            },
    "minecraft:chest" to fun(v, p): Int {
        val tileData = v.getTileData(p) ?: return 0
        return ComparatorCompileHelper.getContainerPower(tileData, 27)
    },
    "minecraft:hopper" to fun(v, p): Int {
        val tileData = v.getTileData(p) ?: return 0
        return ComparatorCompileHelper.getContainerPower(tileData, 5)
    }
).map { BlockState.fromStr(it.first) to it.second }



class ComparatorCompileHelper {

    companion object {
        fun getContainerPower(containerNbt: CompoundTag, containerSlotCount: Int): Int {
            val itemsNbt = containerNbt.getListTag("Items") ?: return 0

            var fullnessSum = 0.0
            for (itemSlotEntry in itemsNbt) {
                itemSlotEntry as CompoundTag
                val itemFullName = itemSlotEntry.getString("id")
                val itemMaxStack = ItemHelper.maxStackSize(itemFullName)
                val count = itemSlotEntry.getByte("Count")
                val fullness = count.toDouble() / itemMaxStack.toDouble()
                fullnessSum += fullness
            }

            val ss = floor(1.0 + ((fullnessSum) / containerSlotCount.toDouble()) * 14.0).toInt().clamp(0, 15)
            return ss
        }

        fun isSsReadable(bs: BlockState) = SsReadable.any { bs.looselyMatches(it.first) }
        fun readSs(v: McVolume, p: IVec3): Int {
            val bs = v.getBlock(p).state
            if (!isSsReadable(bs)) throw Exception("BlockState '${bs}' isn't ss readable")
            return SsReadable.first { bs.looselyMatches(it.first) }.second(v, p)
        }
    }

}