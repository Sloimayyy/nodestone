package com.sloimay.threadstonecore.helpers

class ThscUtils {
    companion object {
        fun toBitString(int: Int): String {
            val s = StringBuilder(Integer.toBinaryString(int).padStart(32, '0'))
            for (j in 0 until 4) {
                s.insert((j)*(8+1), " ")
            }
            return s.toString().slice(1 until s.length)
        }
    }
}