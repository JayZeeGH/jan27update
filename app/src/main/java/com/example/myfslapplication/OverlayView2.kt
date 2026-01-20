package com.example.myfslapplication

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.View
import kotlin.math.min

/**
 * Simplified overlay view that works with FloatArray data
 */
class holder6 @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Drawing paints
    private val handPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }

    private val facePaint = Paint().apply {
        color = Color.BLUE
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    private val posePaint = Paint().apply {
        color = Color.MAGENTA
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }

    private val eyePaint = Paint().apply {
        color = Color.YELLOW
        style = Paint.Style.FILL_AND_STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    private val shoulderPaint = Paint().apply {
        color = Color.CYAN
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    private val landmarkPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
        strokeWidth = 2f
        isAntiAlias = true
    }

    private val debugPaint = Paint().apply {
        color = Color.WHITE
        textSize = 14f
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
    }

    // Simplified data storage
    private var handLandmarks: List<List<FloatArray>> = emptyList()
    private var faceLandmarks: List<FloatArray> = emptyList()
    private var poseLandmarks: List<FloatArray> = emptyList()

    // Commenting out EyeData and ShoulderData for now to avoid type conflicts
    // private var eyeData: EyeData? = null
    // private var shoulderData: ShoulderData? = null

    // View dimensions
    private var viewWidth = 0
    private var viewHeight = 0
    private var imageWidth = 0
    private var imageHeight = 0

    // Debug info
    private var handStatus: String? = null
    private var eyeStatus: String? = null
    private var shoulderStatus: String? = null
    private var processingTime: String? = null

    /**
     * Simplified setter that accepts raw float arrays
     * Commenting out eyeData and shoulderData parameters for now
     */
    fun setAllLandmarks(
        handLandmarks: List<List<FloatArray>>,
        faceLandmarks: List<FloatArray>,
        poseLandmarks: List<FloatArray>,
        // eyeData: Any?,  // Commented out for now
        // shoulderData: Any?,  // Commented out for now
        imageWidth: Int,
        imageHeight: Int
    ) {
        this.handLandmarks = handLandmarks
        this.faceLandmarks = faceLandmarks
        this.poseLandmarks = poseLandmarks
        // this.eyeData = eyeData as? EyeData  // Commented out
        // this.shoulderData = shoulderData as? ShoulderData  // Commented out
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight

        postInvalidate()
    }

    fun setDebugInfo(
        handStatus: String? = null,
        eyeStatus: String? = null,
        shoulderStatus: String? = null,
        processingTime: String? = null
    ) {
        this.handStatus = handStatus
        this.eyeStatus = eyeStatus
        this.shoulderStatus = shoulderStatus
        this.processingTime = processingTime

        postInvalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewWidth = w
        viewHeight = h
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (imageWidth == 0 || imageHeight == 0) return

        // Calculate scale factors
        val scaleX = viewWidth.toFloat() / imageWidth
        val scaleY = viewHeight.toFloat() / imageHeight
        val scale = min(scaleX, scaleY)

        val offsetX = (viewWidth - imageWidth * scale) / 2
        val offsetY = (viewHeight - imageHeight * scale) / 2

        // Save canvas state
        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(scale, scale)

        try {
            // Commenting out eye and shoulder drawing for now
            // drawShoulders(canvas)
            // drawEyes(canvas)
            drawPose(canvas)
            drawFace(canvas)
            drawHands(canvas)

        } catch (e: Exception) {
            Log.e(TAG, "Error drawing landmarks: ${e.message}")
        } finally {
            canvas.restore()
        }

        drawDebugInfo(canvas)
    }

    private fun drawHands(canvas: Canvas) {
        handLandmarks.forEachIndexed { handIndex, hand ->
            hand.forEachIndexed { landmarkIndex, landmark ->
                if (landmark.size >= 2) {
                    val x = landmark[0] * imageWidth
                    val y = landmark[1] * imageHeight

                    // Draw landmark point
                    canvas.drawCircle(x, y, 5f, landmarkPaint)

                    // Draw landmark number for debugging (every 4th point)
                    if (handIndex == 0 && landmarkIndex % 4 == 0) {
                        canvas.drawText(
                            landmarkIndex.toString(),
                            x + 8,
                            y - 8,
                            debugPaint.apply {
                                textSize = 12f
                                color = Color.GREEN
                            }
                        )
                    }
                }
            }
        }
    }

    private fun drawFace(canvas: Canvas) {
        if (faceLandmarks.isEmpty()) return

        // Draw some key face points
        for (i in faceLandmarks.indices step 20) { // Draw every 20th point
            val landmark = faceLandmarks[i]
            if (landmark.size >= 2) {
                val x = landmark[0] * imageWidth
                val y = landmark[1] * imageHeight
                canvas.drawCircle(x, y, 2f, facePaint)
            }
        }
    }

    private fun drawPose(canvas: Canvas) {
        if (poseLandmarks.isEmpty()) return

        // Draw pose landmarks
        poseLandmarks.forEachIndexed { index, landmark ->
            if (landmark.size >= 2) {
                val x = landmark[0] * imageWidth
                val y = landmark[1] * imageHeight

                // Draw larger circles for key points
                val radius = if (index in listOf(11, 12, 13, 14, 15, 16, 23, 24)) {
                    6f // Shoulders, elbows, wrists, hips
                } else {
                    4f
                }

                canvas.drawCircle(x, y, radius, posePaint)
            }
        }

        // Draw connections between key points
        if (poseLandmarks.size >= 33) {
            // Draw shoulder line if we have both shoulder points
            if (poseLandmarks[11].size >= 2 && poseLandmarks[12].size >= 2) {
                canvas.drawLine(
                    poseLandmarks[11][0] * imageWidth,
                    poseLandmarks[11][1] * imageHeight,
                    poseLandmarks[12][0] * imageWidth,
                    poseLandmarks[12][1] * imageHeight,
                    posePaint
                )
            }
        }
    }

    // Commenting out eye drawing for now
    /*
    private fun drawEyes(canvas: Canvas) {
        eyeData?.let { eyes ->
            val leftX = eyes.leftEye.first * imageWidth
            val leftY = eyes.leftEye.second * imageHeight
            canvas.drawCircle(leftX, leftY, 6f, eyePaint)

            val rightX = eyes.rightEye.first * imageWidth
            val rightY = eyes.rightEye.second * imageHeight
            canvas.drawCircle(rightX, rightY, 6f, eyePaint)

            val midX = eyes.midpoint.first * imageWidth
            val midY = eyes.midpoint.second * imageHeight
            canvas.drawCircle(midX, midY, 4f, eyePaint.apply { color = Color.RED })

            canvas.drawLine(leftX, leftY, rightX, rightY, eyePaint.apply {
                color = Color.YELLOW
                strokeWidth = 2f
            })
        }
    }
    */

    // Commenting out shoulder drawing for now
    /*
    private fun drawShoulders(canvas: Canvas) {
        shoulderData?.let { shoulders ->
            val leftX = shoulders.leftShoulder.first * imageWidth
            val leftY = shoulders.leftShoulder.second * imageHeight
            canvas.drawCircle(leftX, leftY, 8f, shoulderPaint)

            val rightX = shoulders.rightShoulder.first * imageWidth
            val rightY = shoulders.rightShoulder.second * imageHeight
            canvas.drawCircle(rightX, rightY, 8f, shoulderPaint)

            val midX = shoulders.midpoint.first * imageWidth
            val midY = shoulders.midpoint.second * imageHeight
            canvas.drawCircle(midX, midY, 6f, shoulderPaint.apply { color = Color.RED })

            canvas.drawLine(leftX, leftY, rightX, rightY, shoulderPaint.apply {
                color = Color.CYAN
                strokeWidth = 4f
            })
        }
    }
    */

    private fun drawDebugInfo(canvas: Canvas) {
        var currentY = 30f

        // Draw status information
        if (handStatus != null) {
            canvas.drawText("👋 $handStatus", 20f, currentY, debugPaint)
            currentY += 20
        }

        if (eyeStatus != null) {
            canvas.drawText("👁 $eyeStatus", 20f, currentY, debugPaint)
            currentY += 20
        }

        if (shoulderStatus != null) {
            canvas.drawText("👤 $shoulderStatus", 20f, currentY, debugPaint)
            currentY += 20
        }

        if (processingTime != null) {
            canvas.drawText("⏱ $processingTime", 20f, currentY, debugPaint)
            currentY += 20
        }

        // Draw landmark counts
        canvas.drawText(
            "Hands: ${handLandmarks.size}, Face: ${faceLandmarks.size}, Pose: ${poseLandmarks.size}",
            20f,
            currentY,
            debugPaint
        )
        currentY += 20

        // Draw image and view dimensions
        canvas.drawText(
            "Image: ${imageWidth}x${imageHeight}, View: ${viewWidth}x${viewHeight}",
            20f,
            currentY,
            debugPaint
        )
    }

    /**
     * Clear all landmarks
     */
    fun clear() {
        handLandmarks = emptyList()
        faceLandmarks = emptyList()
        poseLandmarks = emptyList()
        // eyeData = null
        // shoulderData = null
        postInvalidate()
    }

    companion object {
        private const val TAG = "OverlayView2"
    }
}