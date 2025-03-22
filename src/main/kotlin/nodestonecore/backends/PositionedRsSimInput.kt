package com.sloimay.threadstonecore.backends

import com.sloimay.smath.vectors.IVec3

interface PositionedRsSimInput : RedstoneSimInput {
    val pos: IVec3
}