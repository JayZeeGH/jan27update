package com.example.myfslapplication

import android.content.Context
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraHelper(
    private val context: Context,
    private val previewView: PreviewView,
    private val lifecycleOwner: LifecycleOwner,
    private val onFrameAnalyzed: (ImageProxy) -> Unit
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val TAG = "CameraHelper"

    fun startCamera() {
        try {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

            cameraProviderFuture.addListener({
                try {
                    cameraProvider = cameraProviderFuture.get()
                    bindCameraUseCases()
                    Log.d(TAG, "Camera provider obtained and use cases bound")
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting camera provider: ${e.message}", e)
                }
            }, ContextCompat.getMainExecutor(context))
        } catch (e: Exception) {
            Log.e(TAG, "Error starting camera: ${e.message}", e)
        }
    }

    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: run {
            Log.e(TAG, "Camera provider is null")
            return
        }

        try {
            // Preview use case
            val preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(previewView.display.rotation)
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            // Image analysis use case
            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also { analysisUseCase ->
                    analysisUseCase.setAnalyzer(cameraExecutor) { imageProxy ->
                        try {
                            onFrameAnalyzed(imageProxy)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in frame analyzer: ${e.message}", e)
                            try {
                                imageProxy.close()
                            } catch (closeError: Exception) {
                                Log.e(TAG, "Error closing imageProxy: ${closeError.message}")
                            }
                        }
                    }
                }

            try {
                cameraProvider.unbindAll()

                // CHANGED TO FRONT CAMERA
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,  // Changed from BACK to FRONT
                    preview,
                    imageAnalysis
                )
                Log.d(TAG, "✓ Front camera use cases bound successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error binding camera use cases: ${e.message}", e)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in bindCameraUseCases: ${e.message}", e)
        }
    }

    fun shutdown() {
        try {
            cameraExecutor.shutdown()
            cameraProvider?.unbindAll()
            Log.d(TAG, "Camera helper shutdown")
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down camera: ${e.message}", e)
        }
    }
}