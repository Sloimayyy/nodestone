package com.sloimay.nodestonecore.helpers

import com.google.gson.JsonObject
import com.google.gson.JsonParser


fun parseJsonString(jsonStr: String): JsonObject {
    return JsonParser.parseString(jsonStr).asJsonObject
}