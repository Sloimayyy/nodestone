package com.sloimay.nodestonecore.redstoneir.helpers

import com.sloimay.nodestonecore.helpers.parseJsonString

class ItemHelper {

    companion object {

        private data class PropEntry(val maxStackSize: Int)

        private val itemFullNameToProps = computeItemFullNameToProps()

        private fun computeItemFullNameToProps(): HashMap<String, PropEntry> {
            val itemJsonText = object {}.javaClass.getResource("/minecraft/item_properties.json")?.readText()!!
            val json = parseJsonString(itemJsonText)
            val m = hashMapOf<String, PropEntry>()
            for (itemFullName in json.keySet()) {
                val data = json[itemFullName]!!.asJsonObject
                val maxStackSize = data["max_stack_size"].asInt
                m[itemFullName] = PropEntry(maxStackSize)
            }
            return m
        }

        /*private fun getEntryThrow(itemFullName: String): PropEntry {
            val entry = itemFullNameToProps[itemFullName]
            if (entry == null) {
                throw Exception("Item full name id ''")
            }
        }*/

        fun maxStackSize(itemFullName: String): Int {
            val entry = itemFullNameToProps[itemFullName]
            if (entry == null) {
                return 64
            } else {
                return entry.maxStackSize
            }
        }


    }

}