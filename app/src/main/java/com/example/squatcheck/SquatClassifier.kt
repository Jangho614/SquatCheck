package com.example.squatposedetection

import android.content.Context
import android.util.Log
import com.google.common.reflect.TypeToken
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class SquatClassifier @Throws(IOException::class) constructor(private val context: Context) {

    companion object {
        private const val TAG = "SquatClassifier"
        private const val MODEL_PATH = "squat_model.tflite"
        private const val SCALER_PATH = "scaler_params.json"
    }

    data class Result(
        val className: String,
        val confidence: Float,
        val classIndex: Int
    )

    private var tflite: Interpreter? = null
    private lateinit var inputBuffer: ByteBuffer
    private lateinit var outputBuffer: Array<FloatArray>
    private lateinit var scalerMean: FloatArray
    private lateinit var scalerScale: FloatArray
    private val classNames = arrayOf("Neutral", "Correct Pose", "Incorrect Pose")

    init {
        try {
            // Load model
            val tfliteModel = loadModelFile()

            // Initialize interpreter with GPU acceleration if available
            val tfliteOptions = Interpreter.Options()
            val compatList = CompatibilityList()

            if (compatList.isDelegateSupportedOnThisDevice) {
                val delegateOptions = compatList.bestOptionsForThisDevice
                val gpuDelegate = GpuDelegate(delegateOptions)
                tfliteOptions.addDelegate(gpuDelegate)
                Log.d(TAG, "GPU acceleration enabled")
            } else {
                tfliteOptions.setNumThreads(4)
                Log.d(TAG, "GPU acceleration not available, using CPU")
            }

            tflite = Interpreter(tfliteModel, tfliteOptions)

            // Load scaler parameters
            loadScalerParams()

            // Initialize buffers
            initializeBuffers()

            Log.d(TAG, "SquatClassifier initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize SquatClassifier", e)
            throw e
        }
    }

    @Throws(IOException::class)
    private fun loadModelFile(): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(MODEL_PATH)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    @Throws(IOException::class)
    private fun loadScalerParams() {
        context.assets.open(SCALER_PATH).use { inputStream ->
            val reader = inputStream.bufferedReader()
            val gson = Gson()
            val type = object : TypeToken<Map<String, FloatArray>>() {}.type
            val scalerParams: Map<String, FloatArray> = gson.fromJson(reader, type)

            scalerMean = scalerParams["mean"] ?: throw IllegalStateException("Mean not found in scaler params")
            scalerScale = scalerParams["scale"] ?: throw IllegalStateException("Scale not found in scaler params")

            Log.d(TAG, "Scaler parameters loaded: mean length=${scalerMean.size}, scale length=${scalerScale.size}")
        }
    }

    private fun initializeBuffers() {
        // Input buffer for 20 features (float32)
        inputBuffer = ByteBuffer.allocateDirect(20 * Float.SIZE_BYTES).apply {
            order(ByteOrder.nativeOrder())
        }

        // Output buffer for 3 classes
        outputBuffer = Array(1) { FloatArray(3) }
    }

    fun classify(features: FloatArray): Result {
        if (features.size != 20) {
            Log.e(TAG, "Invalid input features length: ${features.size}")
            return Result("Error", 0.0f, -1)
        }

        val interpreter = tflite ?: return Result("Error", 0.0f, -1)

        // Apply scaling (standardization)
        val scaledFeatures = FloatArray(20) { i ->
            (features[i] - scalerMean[i]) / scalerScale[i]
        }

        // Prepare input buffer
        inputBuffer.rewind()
        scaledFeatures.forEach { feature ->
            inputBuffer.putFloat(feature)
        }

        // Run inference
        return try {
            interpreter.run(inputBuffer, outputBuffer)

            // Find the class with highest probability
            val probabilities = outputBuffer[0]
            val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: -1
            val maxProb = if (maxIndex >= 0) probabilities[maxIndex] else 0.0f

            val className = if (maxIndex >= 0 && maxIndex < classNames.size) {
                classNames[maxIndex]
            } else {
                "Unknown"
            }

            Result(className, maxProb, maxIndex)
        } catch (e: Exception) {
            Log.e(TAG, "Inference failed", e)
            Result("Error", 0.0f, -1)
        }
    }
    fun close() {
        tflite?.close()
        tflite = null
    }
}