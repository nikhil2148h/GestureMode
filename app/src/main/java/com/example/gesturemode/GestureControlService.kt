package com.example.gesturemode

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.util.concurrent.Executors

class GestureControlService : LifecycleService() {

    private lateinit var handLandmarker: HandLandmarker
    private lateinit var overlayManager: OverlayManager
    private lateinit var gestureProcessor: GestureProcessor
    private val executor = Executors.newSingleThreadExecutor()

    companion object {
        const val CHANNEL_ID = "GestureModeChannel"
        const val NOTIFICATION_ID = 101
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GestureMode Active")
            .setContentText("Camera is tracking your hand gestures in the background.")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
            
        // Use standard FOREGROUND_SERVICE without specific type if not needed, 
        // but modern Android enforces FOREGROUND_SERVICE_CAMERA if utilizing camera.
        startForeground(NOTIFICATION_ID, notification)

        // Initialize display metrics
        val displayMetrics = resources.displayMetrics
        SystemController.screenWidth = displayMetrics.widthPixels
        SystemController.screenHeight = displayMetrics.heightPixels

        overlayManager = OverlayManager(this)
        
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        gestureProcessor = GestureProcessor(overlayManager, audioManager)

        setupMediaPipe()
        overlayManager.showOverlays()
        startCamera()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Gesture Control Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun setupMediaPipe() {
        val baseOptionsBuilder = BaseOptions.builder().setModelAssetPath("hand_landmarker.task")
        val baseOptions = baseOptionsBuilder.build()

        val optionsBuilder = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setMinHandDetectionConfidence(0.5f)
            .setMinHandPresenceConfidence(0.5f)
            .setNumHands(1)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener { result, _ ->
                processResult(result)
            }
            .setErrorListener { error ->
                Log.e("GestureService", "MediaPipe Error", error)
            }
            
        handLandmarker = HandLandmarker.createFromOptions(this, optionsBuilder.build())
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analyzer ->
                    analyzer.setAnalyzer(executor) { imageProxy ->
                        val bitmap = imageProxy.toBitmap()
                        val mpImage = BitmapImageBuilder(bitmap).build()
                        val timestamp = imageProxy.imageInfo.timestamp
                        handLandmarker.detectAsync(mpImage, timestamp)
                        imageProxy.close()
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, imageAnalyzer
                )
            } catch (exc: Exception) {
                Log.e("GestureService", "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun processResult(result: HandLandmarkerResult) {
        if (result.landmarks().isNotEmpty()) {
            val landmarks = result.landmarks()[0]
            overlayManager.updateHandLandmarks(landmarks)
            gestureProcessor.processLandmarks(landmarks)
        } else {
            overlayManager.updateHandLandmarks(emptyList()) // clear
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayManager.hideOverlays()
        handLandmarker.close()
        executor.shutdown()
        val cameraProvider = ProcessCameraProvider.getInstance(this).get()
        cameraProvider.unbindAll()
    }
}
