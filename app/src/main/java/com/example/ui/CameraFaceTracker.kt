package com.example.ui

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Lifecycle
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.Executors

import com.google.mlkit.vision.face.FaceLandmark

@OptIn(ExperimentalGetImage::class)
fun startCameraFaceTracking(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    onFaceMoved: (Float, Float) -> Unit
): () -> Unit {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    val executor = Executors.newSingleThreadExecutor()
    
    val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .build()
    val detector = FaceDetection.getClient(options)

    var cameraProvider: ProcessCameraProvider? = null
    var isClosed = false

    cameraProviderFuture.addListener({
        try {
            if (isClosed) {
                detector.close()
                executor.shutdown()
                return@addListener
            }

            val provider = cameraProviderFuture.get()
            cameraProvider = provider

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(executor) { imageProxy ->
                if (isClosed) {
                    imageProxy.close()
                    return@setAnalyzer
                }
                val mediaImage = imageProxy.image
                if (mediaImage != null) {
                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                    detector.process(image)
                        .addOnSuccessListener { faces ->
                            if (!isClosed && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                                if (faces.isNotEmpty()) {
                                    val face = faces.first()
                                    val bounds = face.boundingBox
                                    val imgW = imageProxy.width.toFloat()
                                    val imgH = imageProxy.height.toFloat()
                                    
                                    val nose = face.getLandmark(FaceLandmark.NOSE_BASE)
                                    
                                    val faceCenterX = nose?.position?.x ?: bounds.centerX().toFloat()
                                    val faceCenterY = nose?.position?.y ?: bounds.centerY().toFloat()

                                    val isPortrait = imageProxy.imageInfo.rotationDegrees == 90 || imageProxy.imageInfo.rotationDegrees == 270
                                    val actualW = if (isPortrait) imgH else imgW
                                    val actualH = if (isPortrait) imgW else imgH

                                    val normX = ((faceCenterX / actualW) * 2f - 1f) * -1f
                                    val normY = (faceCenterY / actualH) * 2f - 1f
                                    onFaceMoved(normX, normY)
                                } else {
                                    onFaceMoved(0f, 0f)
                                }
                            }
                        }
                        .addOnCompleteListener {
                            imageProxy.close()
                        }
                } else {
                    imageProxy.close()
                }
            }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            provider.unbindAll()
            if (!isClosed && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    imageAnalysis
                )
            }
        } catch (e: Exception) {
            Log.e("CameraFaceTracker", "Use case binding failed", e)
        }
    }, ContextCompat.getMainExecutor(context))

    return {
        isClosed = true
        try {
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            Log.e("CameraFaceTracker", "Failed to unbind", e)
        }
        try {
            detector.close()
        } catch (e: Exception) {
            Log.e("CameraFaceTracker", "Failed to close detector", e)
        }
        try {
            executor.shutdown()
        } catch (e: Exception) {
            Log.e("CameraFaceTracker", "Failed to shutdown executor", e)
        }
    }
}
