package com.makeeb.engine.gesture

import com.makeeb.core.model.Suggestion
import com.makeeb.engine.layout.LayoutGeometry

/** A touch sample of a swipe, in the same coordinate space as the [LayoutGeometry]. */
data class GesturePoint(val x: Float, val y: Float, val timeMillis: Long)

/** Turns a swipe across the letter keys into ranked word candidates. */
interface GestureDecoder {
    fun decode(path: List<GesturePoint>, geometry: LayoutGeometry, limit: Int = 4): List<Suggestion>
}
