package com.example.myfslapplication

import android.util.Log
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

object DebugLogger {

    fun logFeatures(features: FloatArray) {
        Log.d("FEATURES", features.joinToString(","))
    }

    fun logHand(hand: List<NormalizedLandmark>) {
        hand.forEachIndexed { i, lm ->
            Log.d("HAND", "[$i] x=${lm.x()}, y=${lm.y()}")
        }
    }
}
