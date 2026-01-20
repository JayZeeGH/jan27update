package com.example.myfslapplication

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.framework.image.BitmapImageBuilder

class SimpleHandDetector(context: Context) {
    private var handLandmarker: HandLandmarker? = null

    init {
        initialize(context)
    }

    private fun initialize(context: Context) {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("hand_landmarker.task")
                .setDelegate(Delegate.CPU)
                .build()

            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setMinHandDetectionConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setNumHands(2)
                .setRunningMode(RunningMode.IMAGE) // Simpler mode
                .build()

            handLandmarker = HandLandmarker.createFromOptions(context, options)
            Log.d("SimpleHandDetector", "Initialized successfully")

        } catch (e: Exception) {
            Log.e("SimpleHandDetector", "Initialization failed: ${e.message}")
        }
    }

    fun detect(bitmap: Bitmap): HandLandmarkerResult? {
        return try {
            // Create MPImage using BitmapImageBuilder
            val mpImage = BitmapImageBuilder(bitmap).build()

            // Detect hands
            val result = handLandmarker?.detect(mpImage)

            if (result != null) {
                Log.d("SimpleHandDetector", "Detected ${result.landmarks().size} hands")
            }

            result

        } catch (e: Exception) {
            Log.e("SimpleHandDetector", "Detection failed: ${e.message}")
            null
        }
    }

    fun cleanup() {
        handLandmarker?.close()
    }
}