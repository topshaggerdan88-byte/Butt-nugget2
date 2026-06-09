package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.PresenceFace
import com.example.ui.startCameraFaceTracking
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.PresenceEngine
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PresenceEngine.initialize(this)
        PresenceEngine.startThoughtLoop()
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(darkTheme = true) { // Force dark theme for the cosmic aesthetic
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color(0xFF0F0C1B) // Cosmic Slate Dark
                ) { innerPadding ->
                    PresenceScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun PresenceScreen(
    modifier: Modifier = Modifier
) {
    val emotion by PresenceEngine.emotion.collectAsState()
    val reflection by PresenceEngine.reflection.collectAsState()
    val normalResponse by PresenceEngine.normalResponse.collectAsState()
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

    var userLookX by remember { mutableFloatStateOf(0f) }
    var userLookY by remember { mutableFloatStateOf(0f) }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var faceTrackingEnabled by remember { mutableStateOf(false) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionResultLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasCameraPermission = isGranted
            if (!isGranted) {
                faceTrackingEnabled = false
            }
        }
    )

    DisposableEffect(lifecycleOwner, hasCameraPermission, faceTrackingEnabled) {
        var cameraCleanup: (() -> Unit)? = null
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (faceTrackingEnabled) {
                        if (hasCameraPermission) {
                            cameraCleanup?.invoke()
                            cameraCleanup = startCameraFaceTracking(context, lifecycleOwner) { x, y ->
                                userLookX = x
                                userLookY = y
                            }
                        } else {
                            cameraPermissionResultLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    cameraCleanup?.invoke()
                    cameraCleanup = null
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            cameraCleanup?.invoke()
            cameraCleanup = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { },
                    onDragEnd = {
                        userLookX = 0f
                        userLookY = 0f
                    },
                    onDragCancel = {
                        userLookX = 0f
                        userLookY = 0f
                    }
                ) { change, dragAmount ->
                    change.consume()
                    val sensitivity = 0.006f
                    userLookX = (userLookX + dragAmount.x * sensitivity).coerceIn(-1.0f, 1.0f)
                    userLookY = (userLookY + dragAmount.y * sensitivity).coerceIn(-1.0f, 1.0f)
                }
            }
    ) {
        // Face rendering
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
            userLookX = userLookX,
            userLookY = userLookY,
            isGlitching = isGlitching,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 80.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
        )

        val isImageMode by PresenceEngine.isImageMode.collectAsState()

        Button(
            onClick = {
                PresenceEngine.toggleFaceMode()
            },
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isImageMode) Color(0xFFF44336).copy(alpha = 0.7f) else Color(0xFF3F51B5).copy(alpha = 0.5f)
            )
        ) {
            Text(if (isImageMode) "🌋 Realistic HD 3D" else "🧬 Vector Line 3D")
        }

        Button(
            onClick = {
                faceTrackingEnabled = !faceTrackingEnabled
            },
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (faceTrackingEnabled) Color(0xFF00C853).copy(alpha = 0.6f) else Color.Gray.copy(alpha = 0.4f)
            )
        ) {
            Text(if (faceTrackingEnabled) "👁️ Tracking ON" else "👁️ Tracking OFF")
        }

        Button(
            onClick = {
                if (!Settings.canDrawOverlays(context)) {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                    context.startActivity(intent)
                } else {
                    val overlayIntent = Intent(context, OverlayService::class.java)
                    context.startService(overlayIntent)
                }
            },
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.6f))
        ) {
            Text("Launch Overlay")
        }

        // Textual logs at the bottom
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedContent(
                targetState = emotion,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "emotion_text_anim"
            ) { currentEmotion ->
                Text(
                    text = "EMOTIONAL STATE: ${currentEmotion.uppercase()}",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(10.dp))

            AnimatedContent(
                targetState = reflection,
                transitionSpec = {
                    (slideInVertically { height -> height / 2 } + fadeIn()) togetherWith 
                    (slideOutVertically { height -> -height / 2 } + fadeOut())
                },
                label = "reflection_text_anim"
            ) { currentReflection ->
                Text(
                    text = "\"$currentReflection\"",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    fontStyle = FontStyle.Italic,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // "Thinking" Indicator
            Box(modifier = Modifier.height(16.dp)) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = isThinking,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Text(
                        text = "Processing thoughts...",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            var userText by remember { mutableStateOf("") }
            Row(modifier = Modifier.fillMaxWidth()) {
                androidx.compose.material3.OutlinedTextField(
                    value = userText,
                    onValueChange = { userText = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Enter Query") },
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Red,
                        unfocusedBorderColor = Color.Gray,
                        focusedLabelColor = Color.Red,
                        unfocusedLabelColor = Color.Gray
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                androidx.compose.material3.Button(
                    onClick = {
                        PresenceEngine.generateUserThought(userText)
                        userText = ""
                    },
                    modifier = Modifier.align(Alignment.CenterVertically),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Send")
                }
            }
            if (normalResponse.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                AnimatedContent(
                    targetState = normalResponse,
                    transitionSpec = {
                        (slideInVertically { height -> height / 2 } + fadeIn()) togetherWith 
                        (slideOutVertically { height -> -height / 2 } + fadeOut())
                    },
                    label = "normal_response_anim"
                ) { currentResponse ->
                    Text(
                        text = "NORMAL RESPONSE: \"$currentResponse\"",
                        color = Color.Green,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        fontStyle = FontStyle.Normal,
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}
