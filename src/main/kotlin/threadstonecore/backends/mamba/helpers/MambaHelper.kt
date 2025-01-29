package com.sloimay.threadstonecore.backends.mamba.helpers

class MambaHelper {

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
    }

}



fun Boolean.toInt() = if (this) 1 else 0