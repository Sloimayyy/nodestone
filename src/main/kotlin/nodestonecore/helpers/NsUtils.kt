package com.sloimay.nodestonecore.helpers

import com.sloimay.smath.vectors.IVec2

class NsUtils {
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

fun Boolean.toInt() = if (this) 1 else 0


fun IVec2.rotate90(): IVec2 {
    val c = 0
    val s = 1
    return IVec2(
        this.x * c - this.y * s,
        this.x * s + this.y * c,
    )
}

fun IVec2.rotateM90(): IVec2 {
    val c = 0
    val s = -1
    return IVec2(
        this.x * c - this.y * s,
        this.x * s + this.y * c,
    )
}

