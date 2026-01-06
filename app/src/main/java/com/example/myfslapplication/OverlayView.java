package com.example.myfslapplication;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import java.util.List;

public class OverlayView extends View {
    private HandLandmarkerResult results;
    private final Paint linePaint = new Paint();
    private final Paint pointPaint = new Paint();

    public OverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initPaints();
    }

    private void initPaints() {
        linePaint.setColor(Color.WHITE);
        linePaint.setStrokeWidth(4f);
        linePaint.setStyle(Paint.Style.STROKE);

        pointPaint.setColor(Color.YELLOW);
        pointPaint.setStrokeWidth(8f);
        pointPaint.setStyle(Paint.Style.FILL);
    }

    public void setResults(HandLandmarkerResult results) {
        this.results = results;
        invalidate(); // Redraw the view
    }

    // Inside OverlayView.java - Update the onDraw method
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (results == null || results.landmarks() == null) return;

        // These are the dimensions of the view on your screen
        float width = getWidth();
        float height = getHeight();

        for (List<NormalizedLandmark> landmarks : results.landmarks()) {
            for (NormalizedLandmark landmark : landmarks) {
                // MediaPipe gives 0.0 to 1.0. We multiply by screen width/height.
                float px = landmark.x() * width;
                float py = landmark.y() * height;

                canvas.drawCircle(px, py, 10f, pointPaint); // Draw the yellow dots
            }
        }
    }
}