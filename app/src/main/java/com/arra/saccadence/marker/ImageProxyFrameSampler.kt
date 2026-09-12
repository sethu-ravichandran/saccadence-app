package com.arra.saccadence.marker

import androidx.camera.core.ImageProxy

/**
 * Wraps a live camera frame (YUV_420_888) as a [FrameSampler] so the decoder
 * never touches ImageProxy/plane-stride details directly.
 */
class ImageProxyFrameSampler(private val image: ImageProxy) : FrameSampler {

    override val width: Int = image.width
    override val height: Int = image.height

    private val yPlane = image.planes[0]
    private val uPlane = image.planes[1]
    private val vPlane = image.planes[2]

    override fun lumaAt(x: Int, y: Int): Int {
        val index = y * yPlane.rowStride + x * yPlane.pixelStride
        return yPlane.buffer.get(index).toInt() and 0xFF
    }

    override fun chromaAt(x: Int, y: Int): Pair<Int, Int> {
        val cx = x / 2
        val cy = y / 2
        val uIndex = cy * uPlane.rowStride + cx * uPlane.pixelStride
        val vIndex = cy * vPlane.rowStride + cx * vPlane.pixelStride
        val u = uPlane.buffer.get(uIndex).toInt() and 0xFF
        val v = vPlane.buffer.get(vIndex).toInt() and 0xFF
        return u to v
    }
}
