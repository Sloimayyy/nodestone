package com.sloimay.nodestonecore.backends.shrimple.helpers


class ShrimpleHelper {

    companion object {
        fun toBitsInt(vararg pairs: Pair<Int, Int>): Int {
            var out = 0
            var shift = 0
            for (p in pairs) {
                val mask = (1 shl p.second) - 1
                out = out or ((p.first and mask) shl shift)
                shift += p.second
            }
            return out
        }

        fun decomposeInt(i: Int, vararg fieldLengths: Int): List<Int> {
            val out = mutableListOf<Int>()
            var int = i
            for (fieldLen in fieldLengths) {
                val fieldMask = (1 shl fieldLen) - 1
                out.add(int and fieldMask)
                int = int ushr fieldLen
            }
            return out.toList()
        }

        fun setBitField(i: Int, data: Int, fieldIdx: Int, fieldSize: Int): Int {
            val baseSpan = (1 shl fieldSize) - 1
            val span = baseSpan shl fieldIdx
            return (i and span.inv()) or ((data and baseSpan) shl fieldIdx)
        }

        fun getBitField(i: Int, fieldIdx: Int, fieldSize: Int): Int {
            val baseSpan = (1 shl fieldSize) - 1
            return (i ushr fieldIdx) and baseSpan
        }
    }

}


val Boolean.int
    get() = if (this) 1 else 0
