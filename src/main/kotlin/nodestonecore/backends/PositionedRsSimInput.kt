package com.sloimay.nodestonecore.backends

import com.sloimay.smath.vectors.IVec3

interface PositionedRsSimInput : RedstoneSimInput {
    val pos: IVec3
}