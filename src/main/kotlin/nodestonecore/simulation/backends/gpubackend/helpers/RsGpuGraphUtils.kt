package com.sloimay.nodestonecore.simulation.backends.gpubackend.helpers

class RsGraphUtils {

    companion object {

        fun toBitsInt(vararg pairs: Pair<Int, Int>): Int {
            var out = 0
            var shift = 0
            for (p in pairs) {
                out = out or (p.first shl shift)
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

        fun toBitString(int: Int): String {
            val s = StringBuilder(Integer.toBinaryString(int).padStart(32, '0'))
            for (j in 0 until 4) {
                s.insert((j)*(8+1), " ")
            }
            return s.toString().slice(1 until s.length)
        }

    }

}


fun Boolean.toInt() = if (this) 1 else 0