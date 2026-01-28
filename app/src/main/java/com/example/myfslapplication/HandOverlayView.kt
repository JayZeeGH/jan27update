package com.example.myfslapplication

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * Overlay view for AlphabetActivity.
 */
class HandOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val linePaint = Paint().apply {
        color = Color.CYAN
        strokeWidth = 4f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }
    private val pointPaint = Paint().apply {
        color = Color.MAGENTA
        strokeWidth = 12f
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val textPaint = Paint().apply {
        color = Color.YELLOW
        textSize = 80f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        isFakeBoldText = true
    }

    private var handLandmarks: List<List<FloatArray>> = emptyList()
    private var imageWidth  = 1
    private var imageHeight = 1
    private var overlayLabel = ""

    private val connections = listOf(
        0 to 1, 1 to 2, 2 to 3, 3 to 4,
        0 to 5, 5 to 6, 6 to 7, 7 to 8,
        0 to 9, 9 to 10, 10 to 11, 11 to 12,
        0 to 13, 13 to 14, 14 to 15, 15 to 16,
        0 to 17, 17 to 18, 18 to 19, 19 to 20,
        1 to 5, 5 to 9, 9 to 13, 13 to 17
    )

    fun updateHandLandmarks(
        landmarks: List<List<FloatArray>>,
        imgWidth: Int,
        imgHeight: Int
    ) {
        handLandmarks = landmarks
        imageWidth    = imgWidth
        imageHeight   = imgHeight
        invalidate()
    }

    fun updateLabel(label: String) {
        overlayLabel = label
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (handLandmarks.isEmpty()) return

        val scaleX = width.toFloat()  / imageWidth
        val scaleY = height.toFloat() / imageHeight

        handLandmarks.forEach { hand ->
            if (hand.size == 21) {
                connections.forEach { (start, end) ->
                    val p1 = hand[start]; val p2 = hand[end]
                    canvas.drawLine(
                        p1[0] * imageWidth  * scaleX,
                        p1[1] * imageHeight * scaleY,
                        p2[0] * imageWidth  * scaleX,
                        p2[1] * imageHeight * scaleY,
                        linePaint
                    )
                }
                hand.forEach { point ->
                    canvas.drawCircle(
                        point[0] * imageWidth  * scaleX,
                        point[1] * imageHeight * scaleY,
                        8f, pointPaint
                    )
                }
            }
        }

        if (overlayLabel.isNotEmpty()) {
            canvas.drawText(overlayLabel, width / 2f, 150f, textPaint)
        }
    }

    fun clear() {
        handLandmarks = emptyList()
        overlayLabel  = ""
        invalidate()
    }
}