package com.example.myfslapplication.ml

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class TFLiteClassifier(context: Context) {

    private val interpreter: Interpreter

    init {
        val modelBuffer = loadModel(context, "model.tflite")
        interpreter = Interpreter(modelBuffer)
    }

    private fun loadModel(context: Context, fileName: String): ByteBuffer {
        val assetFileDescriptor = context.assets.openFd(fileName)
        val inputStream = assetFileDescriptor.createInputStream()
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        val buffer = fileChannel.map(
            java.nio.channels.FileChannel.MapMode.READ_ONLY,
            startOffset,
            declaredLength
        )
        return buffer
    }

    /** TEST METHOD — proves TFLite works */
    fun runDummyInference(): FloatArray {
        val input = ByteBuffer.allocateDirect(4 * 10)
            .order(ByteOrder.nativeOrder())

        repeat(10) { input.putFloat(0.0f) }

        val output = Array(1) { FloatArray(5) }
        interpreter.run(input, output)

        return output[0]
    }

    fun close() {
        interpreter.close()
    }
}
