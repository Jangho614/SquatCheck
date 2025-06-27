package com.example.squatposedetection

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.squatcheck.databinding.ActivityMain2Binding
import com.google.common.util.concurrent.ListenableFuture
import com.google.mediapipe.formats.proto.LandmarkProto
import com.google.mediapipe.solutions.pose.Pose
import com.google.mediapipe.solutions.pose.PoseOptions
import com.google.mediapipe.solutions.pose.PoseResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.*

class MainActivity2 : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_CODE_PERMISSIONS = 1001
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    private lateinit var binding: ActivityMain2Binding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private var pose: Pose? = null
    private var squatClassifier: SquatClassifier? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMain2Binding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Initialize MediaPipe Pose
        initializePose()

        // Initialize TensorFlow Lite model
        initializeClassifier()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
    }

    private fun initializePose() {
        val poseOptions = PoseOptions.builder()
            .setStaticImageMode(false)
            .setModelComplexity(1)
            .setSmoothLandmarks(true)
            .setEnableSegmentation(false)
            .setMinDetectionConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .build()

        pose = Pose(this, poseOptions).apply {
            setResultListener { result -> processPoseResult(result) }
            setErrorListener { message, e ->
                Log.e(TAG, "MediaPipe error: $message", e)
            }
        }
    }

    private fun initializeClassifier() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                squatClassifier = SquatClassifier(this@MainActivity2)
                withContext(Dispatchers.Main) {
                    Log.d(TAG, "Classifier initialized successfully")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize classifier", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity2,
                        "모델 초기화 실패: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun processPoseResult(result: PoseResult) {
        if (result.poseLandmarks()?.landmarkList?.isNotEmpty() == true) {
            val features = extractFeatures(result.poseLandmarks().landmarkList)

            features?.let { featureArray ->
                squatClassifier?.let { classifier ->
                    CoroutineScope(Dispatchers.IO).launch {
                        val classificationResult = classifier.classify(featureArray)

                        withContext(Dispatchers.Main) {
                            val resultText = "Pose: ${classificationResult.className}, " +
                                    "Confidence: ${"%.2f".format(classificationResult.confidence)}"
                            binding.resultText.text = resultText
                        }
                    }
                }
            }
        } else {
            runOnUiThread {
                binding.resultText.text = "No pose detected"
            }
        }
    }

    private fun extractFeatures(landmarks: List<LandmarkProto.NormalizedLandmark>): FloatArray? {
        return try {
            // Extract key landmarks (MediaPipe pose landmark indices)
            val rshoulder = floatArrayOf(landmarks[12].x, landmarks[12].y)
            val lshoulder = floatArrayOf(landmarks[11].x, landmarks[11].y)
            val rhip = floatArrayOf(landmarks[24].x, landmarks[24].y)
            val lhip = floatArrayOf(landmarks[23].x, landmarks[23].y)
            val rknee = floatArrayOf(landmarks[26].x, landmarks[26].y)
            val lknee = floatArrayOf(landmarks[25].x, landmarks[25].y)
            val rankle = floatArrayOf(landmarks[28].x, landmarks[28].y)
            val lankle = floatArrayOf(landmarks[27].x, landmarks[27].y)

            // Calculate angles
            val rkneeAngle = 180f - calculateAngle(rhip, rknee, rankle)
            val lkneeAngle = 180f - calculateAngle(lhip, lknee, lankle)
            val rhipAngle = 180f - calculateAngle(rshoulder, rhip, rknee)
            val lhipAngle = 180f - calculateAngle(lshoulder, lhip, lknee)

            // Create feature array (20 features total)
            floatArrayOf(
                rshoulder[0], rshoulder[1], lshoulder[0], lshoulder[1],
                rhip[0], rhip[1], lhip[0], lhip[1],
                rknee[0], rknee[1], lknee[0], lknee[1],
                rankle[0], rankle[1], lankle[0], lankle[1],
                rkneeAngle, lkneeAngle, rhipAngle, lhipAngle
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting features", e)
            null
        }
    }

    private fun calculateAngle(a: FloatArray, b: FloatArray, c: FloatArray): Float {
        val radians = atan2(c[1] - b[1], c[0] - b[0]) - atan2(a[1] - b[1], a[0] - b[0])
        var angle = abs(radians * 180.0 / PI).toFloat()

        if (angle > 180.0f) {
            angle = 360f - angle
        }

        return angle
    }

    private fun startCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .build()

                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    analyzeImage(imageProxy)
                }

                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        this, cameraSelector, preview, imageAnalysis
                    )
                    preview.setSurfaceProvider(binding.previewView.surfaceProvider)
                } catch (e: Exception) {
                    Log.e(TAG, "Use case binding failed", e)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Camera initialization failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyzeImage(imageProxy: ImageProxy) {
        pose?.let { poseDetector ->
            val bitmap = ImageUtils.imageProxyToBitmap(imageProxy)
            poseDetector.send(bitmap)
        }
        imageProxy.close()
    }

    private fun allPermissionsGranted(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pose?.close()
        squatClassifier?.close()
        cameraExecutor.shutdown()
    }
}