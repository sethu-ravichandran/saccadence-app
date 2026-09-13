package com.arra.saccadence.landmark

import android.content.Context
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult

/**
 * One frame's worth of eye/head geometry, in normalized image coordinates
 * (0..1) — deg conversion happens downstream once trial geometry (visual
 * angle per pixel) is known; this layer stays purely about landmarks.
 *
 * Iris centroids give eye position. The outer canthi (eye corners) and nose
 * bridge give a head-pose reference so eye-in-head motion can be separated
 * from head translation, per the plan.
 */
data class LandmarkFrame(
    val phoneTimeMs: Double,
    val leftIrisX: Float,
    val leftIrisY: Float,
    val rightIrisX: Float,
    val rightIrisY: Float,
    val leftOuterCanthusX: Float,
    val rightOuterCanthusX: Float,
    val noseBridgeX: Float,
    val noseBridgeY: Float,
    /** MediaPipe's own per-landmark presence/visibility, collapsed to one figure for the eyes. */
    val confidence: Float,
)

/**
 * Wraps MediaPipe's FaceLandmarker (iris + face mesh) as the app's on-device
 * NPU-accelerated vision pipeline. The model asset
 * (`assets/face_landmarker.task`, MediaPipe's stock float16 build, ~3.6MB)
 * ships in the APK; no network fetch at runtime, consistent with the
 * zero-uploads privacy claim — this only ever reads pixels, it never sends
 * them anywhere.
 *
 * NOT verified against a real camera stream in this session (no device
 * available) — the ImageProxy plane extraction and the specific
 * landmark-index mapping below need an on-device pass before relying on
 * this for the demo. See indices at the bottom against MediaPipe's
 * canonical 478-point face mesh topology.
 */
class EyeLandmarkTracker(context: Context) {

    private val landmarker: FaceLandmarker = FaceLandmarker.createFromOptions(
        context,
        FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetPath("face_landmarker.task")
                    .setDelegate(com.google.mediapipe.tasks.core.Delegate.GPU) // NPU/GPU-accelerated where available; falls back to CPU.
                    .build()
            )
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumFaces(1)
            .setResultListener { result, _ -> lastResult = result; callbackCount++ }
            .setErrorListener { lastError = it; errorCount++ }
            .build()
    )

    @Volatile private var lastResult: FaceLandmarkerResult? = null
    @Volatile var lastError: Throwable? = null
        private set
    /** Increments once per actual detection callback — distinct from how many times [latestFrame] is polled. */
    @Volatile var callbackCount: Int = 0
        private set
    @Volatile var errorCount: Int = 0
        private set
    /** Raw landmark count of the most recent result: -1 if no callback has fired yet, 0 if the last callback found no face. */
    val lastFaceLandmarkCount: Int
        get() {
            val result = lastResult ?: return -1
            return result.faceLandmarks().firstOrNull()?.size ?: 0
        }

    /** Feeds one camera frame in; the result (if any) arrives asynchronously via the listener above. */
    fun analyze(image: ImageProxy, phoneTimeMs: Long) {
        val bitmap = image.toBitmap() // see extension below — YUV_420_888 -> ARGB_8888
        // The analyzer delivers the UNROTATED sensor buffer (rotationDegrees=90
        // in portrait — same fact that forced the marker decoder to full-frame
        // ROI). BlazeFace is rotation-sensitive: fed sideways, it misses faces
        // outright (the on-device "no face detected whatever I do" failure) or
        // returns left/right-scrambled landmarks. Rotate upright first, the
        // same way MediaPipe's own FaceLandmarker sample does.
        val rotationDegrees = image.imageInfo.rotationDegrees
        val upright = if (rotationDegrees != 0) {
            val matrix = android.graphics.Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }
        val mpImage = BitmapImageBuilder(upright).build()
        landmarker.detectAsync(mpImage, phoneTimeMs)
    }

    /** Pulls the most recent completed result and converts it, or null if nothing usable has arrived yet. */
    fun latestFrame(phoneTimeMs: Double): LandmarkFrame? {
        val result = lastResult ?: return null
        val landmarks = result.faceLandmarks().firstOrNull() ?: return null
        if (landmarks.size <= MAX_INDEX_USED) return null

        fun x(i: Int) = landmarks[i].x()
        fun y(i: Int) = landmarks[i].y()
        // presence() is only populated when the graph is configured to emit it;
        // default to fully-confident rather than falsely zeroing out every frame
        // when it isn't, since an absent Optional is not evidence of a blink.
        fun presence(i: Int) = landmarks[i].presence().orElse(1.0f)

        return LandmarkFrame(
            phoneTimeMs = phoneTimeMs,
            leftIrisX = x(LEFT_IRIS_CENTER), leftIrisY = y(LEFT_IRIS_CENTER),
            rightIrisX = x(RIGHT_IRIS_CENTER), rightIrisY = y(RIGHT_IRIS_CENTER),
            leftOuterCanthusX = x(LEFT_OUTER_CANTHUS),
            rightOuterCanthusX = x(RIGHT_OUTER_CANTHUS),
            noseBridgeX = x(NOSE_BRIDGE), noseBridgeY = y(NOSE_BRIDGE),
            confidence = minOf(presence(LEFT_IRIS_CENTER), presence(RIGHT_IRIS_CENTER)),
        )
    }

    fun close() = landmarker.close()

    companion object {
        // MediaPipe's canonical 478-point face mesh (with iris refinement)
        // landmark indices. Cross-check against a live preview before the
        // demo — these are correct per MediaPipe's published topology but
        // unverified on this build's exact model revision.
        private const val LEFT_IRIS_CENTER = 468
        private const val RIGHT_IRIS_CENTER = 473
        private const val LEFT_OUTER_CANTHUS = 263
        private const val RIGHT_OUTER_CANTHUS = 33
        private const val NOSE_BRIDGE = 6
        private const val MAX_INDEX_USED = 473
    }
}
