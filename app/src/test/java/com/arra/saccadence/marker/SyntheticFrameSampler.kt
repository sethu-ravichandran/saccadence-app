package com.arra.saccadence.marker

import kotlin.math.roundToInt

/**
 * Builds a plain-Kotlin luma/chroma image from drawn RGB squares, so the
 * decoder can be tested without a camera, an emulator, or any Android
 * dependency at all.
 */
class SyntheticFrameSampler(
    override val width: Int,
    override val height: Int
) : FrameSampler {

    private val r = IntArray(width * height)
    private val g = IntArray(width * height)
    private val b = IntArray(width * height)

    init {
        fill(0, width, 0, height, 128, 128, 128) // neutral gray background
    }

    fun fill(left: Int, right: Int, top: Int, bottom: Int, red: Int, green: Int, blue: Int) {
        for (y in top until bottom) {
            for (x in left until right) {
                if (x in 0 until width && y in 0 until height) {
                    val i = y * width + x
                    r[i] = red; g[i] = green; b[i] = blue
                }
            }
        }
    }

    fun drawSquare(centerX: Int, centerY: Int, side: Int, red: Int, green: Int, blue: Int) {
        val half = side / 2
        fill(centerX - half, centerX - half + side, centerY - half, centerY - half + side, red, green, blue)
    }

    override fun lumaAt(x: Int, y: Int): Int {
        val i = y * width + x
        return (0.299 * r[i] + 0.587 * g[i] + 0.114 * b[i]).roundToInt().coerceIn(0, 255)
    }

    override fun chromaAt(x: Int, y: Int): Pair<Int, Int> {
        val i = y * width + x
        val u = (-0.169 * r[i] - 0.331 * g[i] + 0.5 * b[i] + 128).roundToInt().coerceIn(0, 255)
        val v = (0.5 * r[i] - 0.419 * g[i] - 0.081 * b[i] + 128).roundToInt().coerceIn(0, 255)
        return u to v
    }

    companion object {
        val WHITE = Triple(255, 255, 255)
        val BLACK = Triple(0, 0, 0)
        val GREEN = Triple(0, 255, 0)
        val RED = Triple(255, 0, 0)
    }
}
