package com.example.myfslapplication;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.*;
import android.os.Bundle;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.core.Delegate;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult;

import org.json.JSONObject;
import org.tensorflow.lite.Interpreter;

import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.util.*;
import java.util.concurrent.*;

@androidx.camera.core.ExperimentalGetImage
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "FSL_MainActivity";
    private static final int CAMERA_PERMISSION_CODE = 100;

    // MATCHES YOUR PYTHON TRAINING SCRIPT
    private static final int IMAGE_SIZE = 64;
    private static final int HOLD_THRESHOLD = 15;
    private static final float CONFIDENCE_THRESHOLD = 0.6f;
    private static final int PADDING = 20;

    private PreviewView previewView;
    private OverlayView overlayView;
    private TextView tvPrediction, tvSentence;
    private ProgressBar progressHold;
    private View btnSpace, btnBackspace, btnClear, btnSpeak, btnBack;

    private Interpreter tflite;
    private HandLandmarker handLandmarker;
    private Map<Integer, String> labelMap = new HashMap<>();
    private ExecutorService cameraExecutor;
    private TextToSpeech tts;

    private final StringBuilder sentence = new StringBuilder();
    private int holdCounter = 0;
    private String lastLabel = null;
    private Bitmap currentFrame;
    private float lastConfidence = 0.0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindViews();
        initTTS();
        cameraExecutor = Executors.newSingleThreadExecutor();

        if (hasCameraPermission()) {
            setupML();
            startCamera();
        } else {
            requestCameraPermission();
        }

        setupButtons();
    }

    private void bindViews() {
        previewView = findViewById(R.id.previewView);
        overlayView = findViewById(R.id.overlayView);
        tvPrediction = findViewById(R.id.tvPrediction);
        tvSentence = findViewById(R.id.tvSentence);
        progressHold = findViewById(R.id.progressHold);
        btnSpace = findViewById(R.id.btnSpace);
        btnBackspace = findViewById(R.id.btnBackspace);
        btnClear = findViewById(R.id.btnClear);
        btnSpeak = findViewById(R.id.btnSpeak);
        btnBack = findViewById(R.id.btnBack);

        progressHold.setMax(HOLD_THRESHOLD);
    }

    private void setupML() {
        try {
            tflite = new Interpreter(loadModel("fsl_model_quantized.tflite"));

            // 1. Loads label mapping (A=0, B=1) from your uploaded JSON
            loadLabelsFromJson("static_metadata.json");

            BaseOptions baseOptions = BaseOptions.builder()
                    .setModelAssetPath("hand_landmarker.task")
                    .setDelegate(Delegate.CPU)
                    .build();

            HandLandmarker.HandLandmarkerOptions options = HandLandmarker.HandLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setMinHandDetectionConfidence(0.5f)
                    .setNumHands(1)
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setResultListener(this::onHandResult)
                    .build();

            handLandmarker = HandLandmarker.createFromOptions(this, options);
            Log.i(TAG, "ML Models Initialized Successfully");
        } catch (Exception e) { Log.e(TAG, "ML Setup Failed", e); }
    }

    private void loadLabelsFromJson(String fileName) throws Exception {
        InputStream is = getAssets().open(fileName);
        byte[] buffer = new byte[is.available()];
        is.read(buffer);
        is.close();
        JSONObject json = new JSONObject(new String(buffer, "UTF-8"));
        JSONObject indexToLabel = json.getJSONObject("index_to_label");

        Iterator<String> keys = indexToLabel.keys();
        while(keys.hasNext()) {
            String key = keys.next();
            labelMap.put(Integer.parseInt(key), indexToLabel.getString(key));
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build();

                analysis.setAnalyzer(cameraExecutor, this::analyzeFrame);

                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis);
            } catch (Exception e) { Log.e(TAG, "Camera Binding Error", e); }
        }, ContextCompat.getMainExecutor(this));
    }

    private void analyzeFrame(ImageProxy proxy) {
        try {
            Bitmap bitmap = Bitmap.createBitmap(proxy.getWidth(), proxy.getHeight(), Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(proxy.getPlanes()[0].getBuffer());

            // 2. Prepare frame: Rotate and Mirror for Front Camera
            Matrix matrix = new Matrix();
            matrix.postRotate(proxy.getImageInfo().getRotationDegrees());
            matrix.postScale(-1, 1);

            currentFrame = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            MPImage img = new BitmapImageBuilder(currentFrame).build();

            // Send to MediaPipe Hand Landmarker
            handLandmarker.detectAsync(img, SystemClock.uptimeMillis());
        } catch (Exception e) { Log.e(TAG, "Analysis Error", e); }
        finally { proxy.close(); }
    }

    private void onHandResult(HandLandmarkerResult result, MPImage image) {
        runOnUiThread(() -> {
            // Update the dots/lines on screen
            overlayView.setResults(result);

            if (result.landmarks().isEmpty()) {
                resetHold();
                tvPrediction.setText("NO HAND DETECTED");
                return;
            }

            // 3. Process detection: Crop hand with padding and classify
            RectF box = boundingBox(result);
            Bitmap cropped = crop(currentFrame, box);
            String label = classify(cropped);

            if (label != null) {
                tvPrediction.setText(String.format(Locale.US, "SIGN: %s (%.0f%%)", label, lastConfidence * 100));
                handleHoldLogic(label);
            } else {
                tvPrediction.setText("WAITING...");
            }
        });
    }

    private String classify(Bitmap bmp) {
        if (bmp == null) return null;
        try {
            // 4. Resize to 64x64 to match training
            Bitmap resized = Bitmap.createScaledBitmap(bmp, IMAGE_SIZE, IMAGE_SIZE, true);
            ByteBuffer buf = ByteBuffer.allocateDirect(4 * IMAGE_SIZE * IMAGE_SIZE * 3).order(ByteOrder.nativeOrder());

            int[] pixels = new int[IMAGE_SIZE * IMAGE_SIZE];
            resized.getPixels(pixels, 0, IMAGE_SIZE, 0, 0, IMAGE_SIZE, IMAGE_SIZE);

            // 5. Normalization: (Pixel / 255.0) as per Python script
            for (int p : pixels) {
                buf.putFloat(((p >> 16) & 0xFF) / 255.0f); // R
                buf.putFloat(((p >> 8) & 0xFF) / 255.0f);  // G
                buf.putFloat((p & 0xFF) / 255.0f);         // B
            }

            float[][] output = new float[1][labelMap.size()];
            tflite.run(buf, output);

            int maxIdx = -1;
            float maxVal = -1;
            for (int i = 0; i < output[0].length; i++) {
                if (output[0][i] > maxVal) {
                    maxVal = output[0][i];
                    maxIdx = i;
                }
            }
            lastConfidence = maxVal;
            return (maxVal > CONFIDENCE_THRESHOLD) ? labelMap.get(maxIdx) : null;
        } catch (Exception e) { return null; }
    }

    private RectF boundingBox(HandLandmarkerResult r) {
        float minX = 1, minY = 1, maxX = 0, maxY = 0;
        for (var p : r.landmarks().get(0)) {
            minX = Math.min(minX, p.x()); minY = Math.min(minY, p.y());
            maxX = Math.max(maxX, p.x()); maxY = Math.max(maxY, p.y());
        }
        int w = currentFrame.getWidth();
        int h = currentFrame.getHeight();

        // 6. Padding: 20px buffer to match Python extraction logic
        return new RectF(
                Math.max(0, minX * w - PADDING),
                Math.max(0, minY * h - PADDING),
                Math.min(w, maxX * w + PADDING),
                Math.min(h, maxY * h + PADDING)
        );
    }

    private void handleHoldLogic(String label) {
        if (label.equals(lastLabel)) {
            holdCounter++;
            progressHold.setProgress(holdCounter);
            if (holdCounter >= HOLD_THRESHOLD) {
                appendText(label);
                holdCounter = 0;
            }
        } else {
            lastLabel = label;
            holdCounter = 0;
            progressHold.setProgress(0);
        }
    }

    private Bitmap crop(Bitmap src, RectF r) {
        try {
            return Bitmap.createBitmap(src, (int)r.left, (int)r.top, (int)r.width(), (int)r.height());
        } catch (Exception e) { return null; }
    }

    private void appendText(String s) {
        sentence.append(s);
        tvSentence.setText(sentence.toString());
        if (tts != null) tts.speak(s, TextToSpeech.QUEUE_ADD, null, null);
    }

    private void resetHold() { holdCounter = 0; lastLabel = null; progressHold.setProgress(0); }

    private void initTTS() {
        tts = new TextToSpeech(this, status -> {
            if(status == TextToSpeech.SUCCESS) tts.setLanguage(Locale.US);
        });
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
    }

    private MappedByteBuffer loadModel(String name) throws IOException {
        AssetFileDescriptor fd = getAssets().openFd(name);
        FileInputStream fis = new FileInputStream(fd.getFileDescriptor());
        return fis.getChannel().map(FileChannel.MapMode.READ_ONLY, fd.getStartOffset(), fd.getDeclaredLength());
    }

    private void setupButtons() {
        btnSpace.setOnClickListener(v -> appendText(" "));
        btnBackspace.setOnClickListener(v -> {
            if(sentence.length() > 0) {
                sentence.deleteCharAt(sentence.length() - 1);
                tvSentence.setText(sentence.toString());
            }
        });
        btnClear.setOnClickListener(v -> {
            sentence.setLength(0);
            tvSentence.setText("");
            resetHold();
        });
        btnSpeak.setOnClickListener(v -> tts.speak(sentence.toString(), TextToSpeech.QUEUE_FLUSH, null, null));

        // BACK BUTTON FIX: Closes current activity and returns to previous one
        btnBack.setOnClickListener(v -> finish());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) cameraExecutor.shutdown();
        if (tflite != null) tflite.close();
        if (handLandmarker != null) handLandmarker.close();
        if (tts != null) tts.shutdown();
    }
}