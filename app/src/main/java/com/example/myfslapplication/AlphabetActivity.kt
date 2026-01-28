package com.example.myfslapplication

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@androidx.camera.core.ExperimentalGetImage
class AlphabetActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "AlphabetActivity"
        private const val PERMISSION_REQUEST = 100
        private const val IMAGE_SIZE = 64
        private const val HOLD_THRESHOLD = 15
        private const val CONFIDENCE_THRESHOLD = 0.9f
        private const val PADDING = 20
    }

    // UI — uses shared HandOverlayView
    private lateinit var previewView: PreviewView
    private lateinit var overlayView: HandOverlayView
    private lateinit var tvPrediction: TextView
    private lateinit var tvConfidence: TextView
    private lateinit var tvSentence: TextView
    private lateinit var tvInstructions: TextView
    private lateinit var progressHold: ProgressBar
    private lateinit var btnSpace: Button
    private lateinit var btnBackspace: Button
    private lateinit var btnSpeak: Button
    private lateinit var btnClear: Button
    private lateinit var btnLanguageToggle: Button
    private lateinit var btnDictionary: Button
    private lateinit var tvHandCount: TextView
    private lateinit var tvHand1Landmarks: TextView
    private lateinit var tvHand2Landmarks: TextView
    private lateinit var tvFaceLandmarks: TextView
    private lateinit var tvShoulderLandmarks: TextView
    private lateinit var tvFeatureInfo: TextView

    // ML — alphabet model
    private var tflite: Interpreter? = null
    private var handLandmarker: HandLandmarker? = null
    private val labelMap = mutableMapOf<Int, String>()
    private lateinit var cameraExecutor: ExecutorService
    private var tts: TextToSpeech? = null
    private var currentLanguage = "english"
    private lateinit var translationHelper: TranslationHelper

    private val sentence = StringBuilder()
    private var holdCounter = 0
    private var lastLabel: String? = null
    private var currentFrame: Bitmap? = null
    private var lastConfidence = 0.0f
    private var isProcessing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alphabet)
        initializeUI()
        initializeEngine()
        checkPermissions()
    }

    private fun initializeUI() {
        previewView         = findViewById(R.id.previewView)
        overlayView         = findViewById(R.id.handOverlayView)
        tvPrediction        = findViewById(R.id.tvPrediction)
        tvConfidence        = findViewById(R.id.tvConfidence)
        tvSentence          = findViewById(R.id.tvSentence)
        tvInstructions      = findViewById(R.id.tvInstructions)
        progressHold        = findViewById(R.id.progressHold)
        btnSpace            = findViewById(R.id.btnSpace)
        btnBackspace        = findViewById(R.id.btnBackspace)
        btnSpeak            = findViewById(R.id.btnSpeak)
        btnClear            = findViewById(R.id.btnClear)
        btnLanguageToggle   = findViewById(R.id.btnLanguageToggle)
        btnDictionary       = findViewById(R.id.btnDictionary)
        tvHandCount         = findViewById(R.id.tvHandCount)
        tvHand1Landmarks    = findViewById(R.id.tvHand1Landmarks)
        tvHand2Landmarks    = findViewById(R.id.tvHand2Landmarks)
        tvFaceLandmarks     = findViewById(R.id.tvFaceLandmarks)
        tvShoulderLandmarks = findViewById(R.id.tvShoulderLandmarks)
        tvFeatureInfo       = findViewById(R.id.tvFeatureInfo)
        progressHold.max    = HOLD_THRESHOLD
        setupButtonListeners()
    }

    private fun setupButtonListeners() {
        btnSpace.setOnClickListener { sentence.append(" "); tvSentence.text = sentence.toString() }
        btnBackspace.setOnClickListener {
            if (sentence.isNotEmpty()) {
                sentence.deleteCharAt(sentence.length - 1)
                tvSentence.text = sentence.toString()
            }
        }
        btnClear.setOnClickListener { sentence.clear(); tvSentence.text = ""; resetHold(); overlayView.clear() }
        btnSpeak.setOnClickListener {
            val t = tvSentence.text.toString()
            if (t.isNotEmpty()) tts?.speak(t, TextToSpeech.QUEUE_FLUSH, null, "TTS")
            else Toast.makeText(this, "No text to speak", Toast.LENGTH_SHORT).show()
        }
        btnLanguageToggle.setOnClickListener { toggleLanguage() }
        btnDictionary.setOnClickListener { openDictionary() }
    }

    private fun toggleLanguage() {
        currentLanguage = if (currentLanguage == "english") "filipino" else "english"
        btnLanguageToggle.text = currentLanguage.uppercase()
        updateTTSLanguage()
        Toast.makeText(this, "Language: ${currentLanguage.replaceFirstChar { it.uppercase() }}", Toast.LENGTH_SHORT).show()
    }

    private fun updateTTSLanguage() {
        val locale = if (currentLanguage == "filipino") Locale("fil", "PH") else Locale.US
        val result = tts?.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Toast.makeText(this, "Filipino TTS not available, using English", Toast.LENGTH_LONG).show()
            tts?.setLanguage(Locale.US); currentLanguage = "english"; btnLanguageToggle.text = "ENGLISH"
        }
    }

    private fun openDictionary() {
        startActivity(Intent(this, DictionaryActivity::class.java)
            .putExtra("MODEL_TYPE", DictionaryActivity.MODEL_TYPE_ALPHABET))
    }

    private fun initializeEngine() {
        cameraExecutor    = Executors.newSingleThreadExecutor()
        tts               = TextToSpeech(this, this)
        translationHelper = TranslationHelper(this)
        setupML()
    }

    private fun setupML() {
        try {
            val bytes = assets.open("fsl_model_quantized.tflite").readBytes()
            val buf   = ByteBuffer.allocateDirect(bytes.size).apply { order(ByteOrder.nativeOrder()); put(bytes); rewind() }
            tflite    = Interpreter(buf, Interpreter.Options().apply { setNumThreads(4); setUseNNAPI(false) })

            val labels = assets.open("labels.txt")
                .bufferedReader().use { it.readText() }
                .split("\n").filter { it.isNotBlank() }.map { it.trim() }
            if (labels.isEmpty()) throw Exception("No alphabet labels found")
            labels.forEachIndexed { i, l -> labelMap[i] = l }
            Log.d(TAG, "✓ ${labelMap.size} alphabet labels loaded")

            handLandmarker = HandLandmarker.createFromOptions(this,
                HandLandmarker.HandLandmarkerOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setDelegate(Delegate.CPU).setModelAssetPath("hand_landmarker.task").build())
                    .setRunningMode(RunningMode.LIVE_STREAM).setNumHands(1)
                    .setMinHandDetectionConfidence(0.5f).setMinHandPresenceConfidence(0.5f).setMinTrackingConfidence(0.5f)
                    .setResultListener { r, _ -> onHandResult(r) }
                    .setErrorListener { e -> Log.e(TAG, "Hand error: ${e.message}") }
                    .build())
        } catch (e: Exception) { Log.e(TAG, "ML Setup Failed", e); showErrorToast("ML Setup Failed: ${e.message}") }
    }

    private fun startCamera() {
        ProcessCameraProvider.getInstance(this).addListener({
            val cp = ProcessCameraProvider.getInstance(this).get()
            val preview  = Preview.Builder().build().apply { setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build().apply { setAnalyzer(cameraExecutor, FrameAnalyzer()) }
            cp.unbindAll()
            cp.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
        }, ContextCompat.getMainExecutor(this))
    }

    private inner class FrameAnalyzer : ImageAnalysis.Analyzer {
        override fun analyze(imageProxy: ImageProxy) {
            if (isProcessing) { imageProxy.close(); return }
            isProcessing = true
            try {
                val bmp    = imageProxy.toBitmap()
                val matrix = Matrix().apply {
                    postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
                    postScale(-1f, 1f, bmp.width / 2f, bmp.height / 2f)
                }
                currentFrame = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
                handLandmarker?.detectAsync(BitmapImageBuilder(currentFrame!!).build(), SystemClock.uptimeMillis())
            } finally { imageProxy.close(); isProcessing = false }
        }
    }

    private fun onHandResult(result: HandLandmarkerResult) {
        runOnUiThread {
            val hands = result.landmarks().map { h -> h.map { floatArrayOf(it.x(), it.y(), it.z()) } }
            currentFrame?.let { overlayView.updateHandLandmarks(hands, it.width, it.height) }
            tvHandCount.text = "Hands: ${result.landmarks().size}"

            if (result.landmarks().isEmpty()) {
                resetHold(); tvPrediction.text = "NO HAND DETECTED"; overlayView.updateLabel(""); return@runOnUiThread
            }

            val label = classify(cropHand(currentFrame, getBoundingBox(result)))
            if (label != null) {
                tvPrediction.text = label
                tvConfidence.text = String.format("%.0f%%", lastConfidence * 100)
                overlayView.updateLabel(label)
                handleHoldLogic(label)
            } else {
                tvPrediction.text = "WAITING..."; overlayView.updateLabel("")
            }
        }
    }

    private fun getBoundingBox(result: HandLandmarkerResult): android.graphics.RectF {
        var minX = 1f; var minY = 1f; var maxX = 0f; var maxY = 0f
        result.landmarks()[0].forEach { minX = minOf(minX, it.x()); minY = minOf(minY, it.y()); maxX = maxOf(maxX, it.x()); maxY = maxOf(maxY, it.y()) }
        val f = currentFrame ?: return android.graphics.RectF()
        return android.graphics.RectF(
            maxOf(0f, minX * f.width - PADDING),  maxOf(0f, minY * f.height - PADDING),
            minOf(f.width.toFloat(), maxX * f.width + PADDING), minOf(f.height.toFloat(), maxY * f.height + PADDING))
    }

    private fun cropHand(frame: Bitmap?, rect: android.graphics.RectF): Bitmap? = try {
        Bitmap.createBitmap(frame!!, rect.left.toInt(), rect.top.toInt(), rect.width().toInt(), rect.height().toInt())
    } catch (e: Exception) { null }

    private fun classify(bitmap: Bitmap?): String? {
        if (bitmap == null || tflite == null) return null
        val resized = Bitmap.createScaledBitmap(bitmap, IMAGE_SIZE, IMAGE_SIZE, true)
        val buf = ByteBuffer.allocateDirect(4 * IMAGE_SIZE * IMAGE_SIZE * 3).order(ByteOrder.nativeOrder())
        val px  = IntArray(IMAGE_SIZE * IMAGE_SIZE)
        resized.getPixels(px, 0, IMAGE_SIZE, 0, 0, IMAGE_SIZE, IMAGE_SIZE)
        for (p in px) { buf.putFloat(((p shr 16) and 0xFF) / 255f); buf.putFloat(((p shr 8) and 0xFF) / 255f); buf.putFloat((p and 0xFF) / 255f) }
        val out = Array(1) { FloatArray(labelMap.size) }
        tflite?.run(buf, out)
        val idx = out[0].indices.maxByOrNull { out[0][it] } ?: 0
        lastConfidence = out[0][idx]
        return if (lastConfidence >= CONFIDENCE_THRESHOLD) labelMap[idx] else null
    }

    private fun handleHoldLogic(label: String) {
        if (label == lastLabel) {
            holdCounter++; progressHold.progress = holdCounter
            if (holdCounter >= HOLD_THRESHOLD) {
                sentence.append(label); tvSentence.text = sentence.toString()
                val spoken = try {
                    if (translationHelper.hasTranslation(label))
                        translationHelper.getTranslation(label, currentLanguage)?.takeIf { it.isNotBlank() } ?: label
                    else label
                } catch (e: Exception) { label }
                tts?.speak(spoken, TextToSpeech.QUEUE_ADD, null, null)
                holdCounter = 0; progressHold.progress = 0
            }
        } else { lastLabel = label; holdCounter = 1; progressHold.progress = 1 }
    }

    private fun resetHold() { holdCounter = 0; lastLabel = null; progressHold.progress = 0 }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera()
        else ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), PERMISSION_REQUEST)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startCamera()
    }

    override fun onInit(status: Int) { if (status == TextToSpeech.SUCCESS) updateTTSLanguage() }

    private fun showErrorToast(msg: String) = runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown(); handLandmarker?.close(); tflite?.close(); tts?.shutdown()
    }
}