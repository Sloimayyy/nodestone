package com.sloimay.nodestonecore.helpers

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser

fun parseJsonString(jsonStr: String): HashMap<String, Any?> {
    val parser = Parser.default()
    val sb = StringBuilder(jsonStr)
    val jsonObject = parser.parse(sb) as JsonObject
    return HashMap(jsonObject)
}