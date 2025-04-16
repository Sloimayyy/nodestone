package com.sloimay.nodestonecore.redstoneir.helpers

import com.sloimay.mcvolume.block.BlockState
import com.sloimay.nodestonecore.helpers.parseJsonString





class BsHelper {

    companion object {

        private data class BsPropEntry(val conductive: Boolean)

        private val bsFullNameToProps = computeBsFullNameToProps()


        /**
         * Used to think it wouldn't work if we implemented nodestone into a minecraft plugin;
         * but it's actually fine because the nodestone jar is separate.
         */
        private fun computeBsFullNameToProps(): HashMap<String, BsPropEntry> {
            val blocksJsonText = object {}.javaClass.getResource("/minecraft/bs_fullnames_to_data.json")?.readText()!!
            val json = parseJsonString(blocksJsonText)

            val m = hashMapOf<String, BsPropEntry>()

            for (bsFullName in json.keySet()) {
                val data = json[bsFullName]!!.asJsonObject
                val conductive = data["conductive"].asBoolean
                m[bsFullName] = BsPropEntry(conductive)
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


fun BlockState.isConductive() = BsHelper.isConductive(this)
fun BlockState.isRsTransparent() = BsHelper.isRsTransparent(this)