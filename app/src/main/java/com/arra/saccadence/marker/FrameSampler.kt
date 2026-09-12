package com.arra.saccadence.marker

/**
 * A pixel source the decoder can query by coordinate, independent of where
 * the pixels actually came from. Keeping this Android-free means the
 * decoding logic can be exercised in a plain JVM test against a synthetic
 * image, with the real camera frame plugged in only at the call site.
 */
interface FrameSampler {
    val width: Int
    val height: Int

    /** Luma (brightness), 0-255. */
    fun lumaAt(x: Int, y: Int): Int

    /** Chroma as a (u, v) pair, each 0-255, BT.601 convention. */
    fun chromaAt(x: Int, y: Int): Pair<Int, Int>
}
