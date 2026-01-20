package com.example.myfslapplication

import android.content.Context
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

class HandLandmarkerHelper(
    val context: Context,
    var runningMode: RunningMode = RunningMode.LIVE_STREAM,
    var handLandmarkerHelperListener: LandmarkerListener? = null
) {
    private var handLandmarker: HandLandmarker? = null

    init {
        setupHandLandmarker()
    }

    fun setupHandLandmarker() {
        val baseOptionsBuilder = BaseOptions.builder().setModelAssetPath("hand_landmarker.task")
        val optionsBuilder = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(baseOptionsBuilder.build())
            .setMinHandDetectionConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setMinHandPresenceConfidence(0.5f)
            .setNumHands(2)
            .setRunningMode(runningMode)

        if (runningMode == RunningMode.LIVE_STREAM) {
            optionsBuilder.setResultListener(this::returnLivestreamResult)
        }

        handLandmarker = HandLandmarker.createFromOptions(context, optionsBuilder.build())
    }

    fun detectLiveStream(imageProxy: ImageProxy) {
        val frameTime = SystemClock.uptimeMillis()
        val bitmapBuffer = BitmapImageBuilder(imageProxy.toBitmap()).build()
        handLandmarker?.detectAsync(bitmapBuffer, frameTime)
        imageProxy.close()
    }

    private fun returnLivestreamResult(result: HandLandmarkerResult, input: com.google.mediapipe.framework.image.MPImage) {
        handLandmarkerHelperListener?.onResults(ResultBundle(listOf(result)))
    }

    fun clear() {
        handLandmarker?.close()
        handLandmarker = null
    }

    interface LandmarkerListener {
        fun onError(error: String)
        fun onResults(resultBundle: ResultBundle)
    }

    data class ResultBundle(val results: List<HandLandmarkerResult>)
}