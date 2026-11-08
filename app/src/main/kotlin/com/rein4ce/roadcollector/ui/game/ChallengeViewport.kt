package com.rein4ce.roadcollector.ui.game

import com.rein4ce.roadcollector.core.challenge.ChallengeBounds

/** Same whole-tower-fit policy as #70, including overhangs below and beside the platform. */
internal fun ChallengeBounds.pixelsPerMeter(width: Float, height: Float): Float {
    if (width <= 0 || height <= 0) return 0f
    return minOf(width / (2 * halfWidth), height * .78 / top, height * .22 / -bottom).toFloat()
}
