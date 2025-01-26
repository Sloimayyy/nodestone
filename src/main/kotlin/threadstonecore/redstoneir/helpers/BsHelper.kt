package com.sloimay.threadstonecore.redstoneir.helpers

import com.beust.klaxon.JsonObject
import com.sloimay.threadstonecore.helpers.parseJsonString
import me.sloimay.mcvolume.block.BlockState


class BsHelper {

    companion object {

        private data class PropEntry(val conductive: Boolean)

        private val bsFullNameToProps = computeBsFullNameToProps()

        private fun computeBsFullNameToProps(): HashMap<String, PropEntry> {
            val blocksJsonText = object {}.javaClass.getResource("/minecraft/bs_fullnames_to_data.json")?.readText()!!
            val json = parseJsonString(blocksJsonText)

            val m = hashMapOf<String, PropEntry>()

            for ((bsFullName, data) in json) {
                data as JsonObject
                val conductive = data["conductive"] as Boolean
                m[bsFullName] = PropEntry(conductive)
            }

            return m
        }

        fun isConductive(blockState: BlockState): Boolean {
            // Defaults to true if the block isn't known
            return bsFullNameToProps[blockState.fullName]?.conductive ?: true
        }
        fun isRsTransparent(bs: BlockState) = !isConductive(bs)
    }

}