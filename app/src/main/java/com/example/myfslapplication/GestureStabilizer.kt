package com.example.myfslapplication

import android.util.Log

/**
 * Gesture stabilizer matching Python implementation
 * - Requires 3 consecutive frames of same gesture
 * - Confidence threshold: 0.40
 * - Prevents rapid flickering
 */
class holder5 {

    private var stableGesture: String? = null
    private var stableGestureCount = 0
    private val stabilityThreshold = 3
    private val confidenceThreshold = 0.40f

    private var lastStableGesture: String? = null
    private var lastStableTime = 0L
    private val cooldownMs = 1000L

    companion object {
        private const val TAG = "GestureStabilizer"
    }

    /**
     * Update stabilizer with new prediction
     * Returns stable gesture only when confirmed by multiple frames
     */
    fun update(label: String, confidence: Float): String? {
        if (confidence < confidenceThreshold) {
            if (stableGestureCount > 0) {
                Log.d(TAG, "Confidence dropped to ${(confidence * 100).toInt()}%, resetting counter")
            }
            stableGesture = null
            stableGestureCount = 0
            return null
        }

        if (label == stableGesture) {
            stableGestureCount++
        } else {
            if (stableGesture != null) {
                Log.d(TAG, "Gesture changed: $stableGesture → $label (restarting counter)")
            }
            stableGesture = label
            stableGestureCount = 1
        }

        if (stableGestureCount < stabilityThreshold) {
            Log.d(TAG, "Building stability for '$label': $stableGestureCount/$stabilityThreshold")
        }

        if (stableGestureCount >= stabilityThreshold) {
            val currentTime = System.currentTimeMillis()

            if (label == lastStableGesture &&
                (currentTime - lastStableTime) < cooldownMs) {
                Log.d(TAG, "Cooldown active for '$label', skipping")
                return null
            }

            Log.d(TAG, "✓ STABLE GESTURE CONFIRMED: $label (confidence: ${(confidence * 100).toInt()}%)")

            lastStableGesture = label
            lastStableTime = currentTime

            stableGesture = null
            stableGestureCount = 0

            return label
        }

        return null
    }

    /**
     * Get current stability progress (0.0 to 1.0)
     */
    fun getStabilityProgress(): Float {
        if (stableGesture == null) return 0f
        return stableGestureCount.toFloat() / stabilityThreshold.toFloat()
    }
}