package com.example.myfslapplication

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

class PoseHandLandmarkerHelper(
    private val context: Context,
    private val runningMode: RunningMode = RunningMode.LIVE_STREAM
) {

    private val TAG = "PoseHandHelper"

    private var poseLandmarker: PoseLandmarker? = null
    private var handLandmarker: HandLandmarker? = null

    // Thread-safe latest results
    @Volatile var latestPoseResult: PoseLandmarkerResult? = null
        private set

    @Volatile var latestHandResult: HandLandmarkerResult? = null
        private set

    init {
        setupPoseLandmarker()
        setupHandLandmarker()
    }

    // ---------------- POSE ----------------

    private fun setupPoseLandmarker() {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("pose_landmarker.task")
                .setDelegate(Delegate.GPU)
                .build()

            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setMinPoseDetectionConfidence(0.5f)
                .setMinPosePresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setRunningMode(runningMode)
                .setResultListener { result, _ ->
                    latestPoseResult = result
                    val count = result.landmarks().firstOrNull()?.size ?: 0
                    Log.d(TAG, "✓ Pose detected: $count landmarks")
                }
                .setErrorListener {
                    Log.e(TAG, "Pose error: ${it.message}")
                }
                .build()

            poseLandmarker = PoseLandmarker.createFromOptions(context, options)
            Log.d(TAG, "✓ Pose landmarker initialized")

        } catch (e: Exception) {
            Log.e(TAG, "✗ Pose setup failed", e)
        }
    }

    // ---------------- HANDS ----------------

    private fun setupHandLandmarker() {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("hand_landmarker.task")
                .setDelegate(Delegate.GPU)
                .build()

            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setMinHandDetectionConfidence(0.5f)
                .setMinHandPresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setNumHands(2)
                .setRunningMode(runningMode)
                .setResultListener { result, _ ->
                    latestHandResult = result
                    Log.d(TAG, "✓ Hands detected: ${result.landmarks().size}")
                }
                .setErrorListener {
                    Log.e(TAG, "Hand error: ${it.message}")
                }
                .build()

            handLandmarker = HandLandmarker.createFromOptions(context, options)
            Log.d(TAG, "✓ Hand landmarker initialized")

        } catch (e: Exception) {
            Log.e(TAG, "✗ Hand setup failed", e)
        }
    }

    // ---------------- DETECTION ----------------

    fun detectLiveStream(imageProxy: ImageProxy) {
        try {
            val frameTime = SystemClock.uptimeMillis()
            val bitmap = imageProxy.toBitmap()
            val mpImage = BitmapImageBuilder(bitmap).build()

            poseLandmarker?.detectAsync(mpImage, frameTime)
            handLandmarker?.detectAsync(mpImage, frameTime)

        } catch (e: Exception) {
            Log.e(TAG, "Detection error", e)
        }
    }

    // ---------------- CLEANUP ----------------

    fun clear() {
        try {
            poseLandmarker?.close()
            handLandmarker?.close()

            poseLandmarker = null
            handLandmarker = null
            latestPoseResult = null
            latestHandResult = null

            Log.d(TAG, "✓ Pose + Hand helpers cleared")

        } catch (e: Exception) {
            Log.e(TAG, "Clear error", e)
        }
    }
}
