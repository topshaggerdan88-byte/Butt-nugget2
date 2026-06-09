package com.example

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.ui.PresenceFace
import com.example.viewmodel.PresenceEngine

class OverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private lateinit var windowManager: WindowManager
    private lateinit var composeView: ComposeView

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        PresenceEngine.initialize(this)

        val params = WindowManager.LayoutParams(
            400,
            400,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 100
        }

        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeViewModelStoreOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setContent {
                val emotion by PresenceEngine.emotion.collectAsState()
                val intensity by PresenceEngine.intensity.collectAsState()
                val isThinking by PresenceEngine.isThinking.collectAsState()
                val isSpeaking by PresenceEngine.isSpeaking.collectAsState()
                val speechVolume by PresenceEngine.speechVolume.collectAsState()
                val eyeColorHex by PresenceEngine.eyeColorHex.collectAsState()
                val auraColorHex by PresenceEngine.auraColorHex.collectAsState()
                val eyeSlant by PresenceEngine.eyeSlant.collectAsState()
                val mouthCurve by PresenceEngine.mouthCurve.collectAsState()
                val pupilSize by PresenceEngine.pupilSize.collectAsState()
                val isGlitching by PresenceEngine.isGlitching.collectAsState()
                
                Box(modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            params.x += dragAmount.x.toInt()
                            params.y += dragAmount.y.toInt()
                            windowManager.updateViewLayout(composeView, params)
                        }
                    }, 
                    contentAlignment = Alignment.Center
                ) {
                    PresenceFace(
                        emotion = emotion,
                        intensity = intensity,
                        isThinking = isThinking,
                        isSpeaking = isSpeaking,
                        speechVolume = speechVolume,
                        eyeColorHex = eyeColorHex,
                        auraColorHex = auraColorHex,
                        eyeSlant = eyeSlant,
                        mouthCurveState = mouthCurve,
                        pupilSize = pupilSize,
                        userLookX = 0f, 
                        userLookY = 0f,
                        isGlitching = isGlitching,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        windowManager.addView(composeView, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        windowManager.removeView(composeView)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
