package com.example.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.R
import com.example.viewmodel.PresenceEngine
import kotlinx.coroutines.delay
import kotlin.random.Random

// Helper to parse hex colors securely
fun parseHexColor(hexString: String, defaultColor: Color): Color {
    return try {
        val cleanHex = if (hexString.startsWith("#")) hexString.substring(1) else hexString
        val colorInt = android.graphics.Color.parseColor("#$cleanHex")
        Color(colorInt)
    } catch (e: Exception) {
        defaultColor
    }
}

// 3D Projection Engine
fun project3D(
    x: Float, y: Float, z: Float,
    yaw: Float, pitch: Float,
    centerX: Float = 200f, centerY: Float = 200f,
    cameraDistance: Float = 470f
): Offset {
    val dx = x - centerX
    val dy = y - centerY
    val dz = z

    // 1. Rotate around Y-axis (Yaw - horizontal panning)
    val cosY = kotlin.math.cos(yaw)
    val sinY = kotlin.math.sin(yaw)
    val rx = dx * cosY - dz * sinY
    val rz1 = dx * sinY + dz * cosY

    // 2. Rotate around X-axis (Pitch - vertical tilting)
    val cosX = kotlin.math.cos(pitch)
    val sinX = kotlin.math.sin(pitch)
    val ry = dy * cosX + rz1 * sinX
    val rz = -dy * sinX + rz1 * cosX

    // 3. Perspective correction factor
    val factor = cameraDistance / (cameraDistance + rz)
    
    return Offset(centerX + rx * factor, centerY + ry * factor)
}

// Perspective scaling factor helper for circles, widths, lines
fun getPerspectiveScale(
    z: Float,
    yaw: Float, pitch: Float,
    cameraDistance: Float = 470f
): Float {
    val cosY = kotlin.math.cos(yaw)
    val cosX = kotlin.math.cos(pitch)
    val rz = z * cosY * cosX
    return cameraDistance / (cameraDistance + rz)
}

fun Path.moveTo3D(x: Float, y: Float, z: Float, yaw: Float, pitch: Float) {
    val pt = project3D(x, y, z, yaw, pitch)
    moveTo(pt.x, pt.y)
}

fun Path.lineTo3D(x: Float, y: Float, z: Float, yaw: Float, pitch: Float) {
    val pt = project3D(x, y, z, yaw, pitch)
    lineTo(pt.x, pt.y)
}

fun Path.cubicTo3D(
    cx1: Float, cy1: Float, cz1: Float,
    cx2: Float, cy2: Float, cz2: Float,
    x: Float, y: Float, z: Float,
    yaw: Float, pitch: Float
) {
    val pt1 = project3D(cx1, cy1, cz1, yaw, pitch)
    val pt2 = project3D(cx2, cy2, cz2, yaw, pitch)
    val end = project3D(x, y, z, yaw, pitch)
    cubicTo(pt1.x, pt1.y, pt2.x, pt2.y, end.x, end.y)
}

fun Path.quadraticTo3D(
    cx: Float, cy: Float, cz: Float,
    x: Float, y: Float, z: Float,
    yaw: Float, pitch: Float
) {
    val ctrl = project3D(cx, cy, cz, yaw, pitch)
    val end = project3D(x, y, z, yaw, pitch)
    quadraticTo(ctrl.x, ctrl.y, end.x, end.y)
}

@Composable
fun PresenceFace(
    emotion: String,
    intensity: Float,
    isThinking: Boolean,
    isSpeaking: Boolean = false,
    speechVolume: Float = 0f,
    eyeColorHex: String,
    auraColorHex: String,
    eyeSlant: Float,
    mouthCurveState: Float,
    pupilSize: Float,
    userLookX: Float = 0f,
    userLookY: Float = 0f,
    isGlitching: Boolean = false,
    modifier: Modifier = Modifier
) {
    val defaultEyeColor = Color(0xFFFF0000)
    val defaultAuraColor = Color(0xFF4A0000)
    
    val targetEyeColor = parseHexColor(eyeColorHex, defaultEyeColor)
    val targetAuraColor = parseHexColor(auraColorHex, defaultAuraColor)

    val animatedEyeColor by animateColorAsState(
        targetValue = targetEyeColor,
        animationSpec = tween(durationMillis = 1500, easing = LinearOutSlowInEasing),
        label = "eye_color"
    )
    
    val animatedAuraColor by animateColorAsState(
        targetValue = targetAuraColor,
        animationSpec = tween(durationMillis = 2000, easing = LinearOutSlowInEasing),
        label = "aura_color"
    )

    val animatedMouthCurve by animateFloatAsState(
        targetValue = mouthCurveState,
        animationSpec = tween(1500),
        label = "mouth_curve_state"
    )

    // Smooth head movement tracking
    val trackingOffsetX by animateFloatAsState(targetValue = userLookX, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), label = "track_x")
    val trackingOffsetY by animateFloatAsState(targetValue = userLookY, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), label = "track_y")

    // Breathing animation
    val infiniteTransition = rememberInfiniteTransition(label = "breathing_transition")
    val breath by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath_scale"
    )

    // Thinking pulsing
    val thinkingPulse by infiniteTransition.animateFloat(
        initialValue = if (isThinking) 0.6f else 0.8f,
        targetValue = if (isThinking) 1.0f else 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isThinking) 800 else 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "thinking_pulse"
    )

    // Occasional subtle shaking based on intensity
    var shakeOffset by remember { mutableStateOf(0f) }
    LaunchedEffect(intensity, isThinking) {
        while (true) {
            if (intensity > 0.7f || isThinking) {
                shakeOffset = Random.nextFloat() * 4f - 2f
                delay(50)
                shakeOffset = Random.nextFloat() * 4f - 2f
                delay(50)
                shakeOffset = 0f
            }
            delay(Random.nextLong(200, 1000))
        }
    }

    // Mouth Glow for speaking based on volume
    val lipSyncPulse by animateFloatAsState(
        targetValue = speechVolume,
        animationSpec = tween(50),
        label = "lip_sync"
    )

    // Glitch CSS-like keyframe simulation states
    var glitchOffsetState by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var glitchScaleState by remember { mutableStateOf(androidx.compose.ui.geometry.Offset(1f, 1f)) }
    var glitchAlpha by remember { mutableStateOf(1f) }
    var chromaticAberrationOffset by remember { mutableStateOf(0f) }
    var glitchTintShift by remember { mutableStateOf(false) }

    // Helper data class for vertical visual corruption bars
    data class GlitchBar(
        val topPercent: Float,
        val heightPercent: Float,
        val widthPercent: Float,
        val offsetPercent: Float,
        val color: Color
    )

    var glitchBars by remember { mutableStateOf(emptyList<GlitchBar>()) }

    LaunchedEffect(isGlitching) {
        if (isGlitching) {
            val random = Random(System.currentTimeMillis())
            while (true) {
                // Rapidly randomize positions/offsets representing digital corruption Jitter
                glitchOffsetState = androidx.compose.ui.geometry.Offset(
                    (random.nextFloat() * 40f - 20f),
                    (random.nextFloat() * 30f - 15f)
                )
                
                // Rapidly resize/stretch representing buffer errors
                glitchScaleState = androidx.compose.ui.geometry.Offset(
                    if (random.nextFloat() > 0.8f) (0.85f + random.nextFloat() * 0.3f) else 1f,
                    if (random.nextFloat() > 0.8f) (0.85f + random.nextFloat() * 0.3f) else 1f
                )
                
                // Random alpha drops/flickering scanline effect
                glitchAlpha = if (random.nextFloat() > 0.8f) random.nextFloat().coerceIn(0.2f, 0.9f) else 1f
                
                // RGB Split distances representation
                chromaticAberrationOffset = if (random.nextFloat() > 0.3f) (random.nextFloat() * 26f - 13f) else 0f
                
                // Neon system-alert color scheme injection
                glitchTintShift = random.nextFloat() > 0.65f
                
                // Data memory blocks corruption overlays
                glitchBars = if (random.nextFloat() > 0.25f) {
                    List(random.nextInt(1, 5)) {
                        GlitchBar(
                            topPercent = random.nextFloat(),
                            heightPercent = random.nextFloat() * 0.12f + 0.02f,
                            widthPercent = random.nextFloat() * 0.6f + 0.4f,
                            offsetPercent = random.nextFloat() * 0.3f - 0.15f,
                            color = when (random.nextInt(4)) {
                                0 -> Color(0xFF00FFFF) // Cyan
                                1 -> Color(0xFFFF00FF) // Magenta
                                2 -> Color(0xFF00FF00) // Neon Lime Green
                                else -> Color(0xFFFFFFFF) // White
                            }.copy(alpha = random.nextFloat() * 0.8f + 0.2f)
                        )
                    }
                } else {
                    emptyList()
                }
                
                // Render framing speed matching rapid CSS @keyframes jittering (30ms - 80ms)
                delay(random.nextLong(30, 80))
            }
        } else {
            glitchOffsetState = androidx.compose.ui.geometry.Offset.Zero
            glitchScaleState = androidx.compose.ui.geometry.Offset(1f, 1f)
            glitchAlpha = 1f
            chromaticAberrationOffset = 0f
            glitchTintShift = false
            glitchBars = emptyList()
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Background Aura
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 1.5f * breath
            val auraCenterX = (size.width / 2f) + (if (isGlitching) glitchOffsetState.x * 0.5f else 0f)
            val auraCenterY = (size.height / 2f) + (if (isGlitching) glitchOffsetState.y * 0.5f else 0f)
            
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        (if (isGlitching && glitchTintShift) Color.Green else animatedAuraColor).copy(alpha = 0.6f * thinkingPulse + (intensity * 0.2f)),
                        Color.Transparent
                    ),
                    center = androidx.compose.ui.geometry.Offset(auraCenterX, auraCenterY),
                    radius = radius
                ),
                radius = radius,
                center = androidx.compose.ui.geometry.Offset(auraCenterX, auraCenterY)
            )
        }

        // Interactive 3D Background Holographic HUD Grid (Inverse Parallax)
        Canvas(
            modifier = Modifier
                .fillMaxSize(0.95f)
                .graphicsLayer {
                    // Motion Parallax: shift in the opposite direction of tracking movement
                    translationX = -trackingOffsetX * 25f
                    translationY = -trackingOffsetY * 25f
                    // Slight perspective twist opposite to head movement to exaggerate depth separation
                    rotationY = trackingOffsetX * 12f
                    rotationX = trackingOffsetY * -12f
                    cameraDistance = 15f * density
                }
        ) {
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            val baseRadius = size.minDimension * 0.44f
            
            // Draw digital system alignment matrix points
            val gridSpacing = size.minDimension * 0.08f
            val gridColor = animatedAuraColor.copy(alpha = 0.16f * thinkingPulse)
            for (x in (0..12)) {
                for (y in (0..12)) {
                    val ptX = centerX + (x - 6) * gridSpacing
                    val ptY = centerY + (y - 6) * gridSpacing
                    drawCircle(
                        color = gridColor,
                        radius = 2f,
                        center = androidx.compose.ui.geometry.Offset(ptX, ptY)
                    )
                }
            }

            // Tech reticle targeting circles
            drawCircle(
                color = animatedEyeColor.copy(alpha = 0.15f),
                radius = baseRadius,
                center = androidx.compose.ui.geometry.Offset(centerX, centerY),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 2f,
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(15f, 25f), 0f)
                )
            )

            drawCircle(
                color = animatedEyeColor.copy(alpha = 0.08f),
                radius = baseRadius * 0.72f,
                center = androidx.compose.ui.geometry.Offset(centerX, centerY),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 1f,
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 15f), 5f)
                )
            )

            // Dynamic threat scan lines sweeping vertically
            val sweepY = centerY + (baseRadius * kotlin.math.sin(breath * 6.28318f))
            drawLine(
                color = animatedEyeColor.copy(alpha = 0.22f),
                start = androidx.compose.ui.geometry.Offset(centerX - baseRadius * 0.82f, sweepY),
                end = androidx.compose.ui.geometry.Offset(centerX + baseRadius * 0.82f, sweepY),
                strokeWidth = 3f
            )
        }

        val maxHeadTurn = 30f // pixels
        val maxEyeTurn = 50f // pixels (parallax effect)

        // 3D unified stage box container using dynamic 3D vertex projections
        Box(
            modifier = Modifier
                .fillMaxSize(0.85f)
                .graphicsLayer {
                    scaleX = breath * (if (isGlitching) glitchScaleState.x else 1f)
                    scaleY = breath * (if (isGlitching) glitchScaleState.y else 1f)
                    translationX = shakeOffset + (trackingOffsetX * maxHeadTurn) + (if (isGlitching) glitchOffsetState.x else 0f)
                    translationY = shakeOffset + (trackingOffsetY * maxHeadTurn) + (if (isGlitching) glitchOffsetState.y else 0f)
                    
                    alpha = if (isGlitching) glitchAlpha else 1f
                },
            contentAlignment = Alignment.Center
        ) {
            val yaw = trackingOffsetX * -0.55f // Negative horizontal rotation for face-focus parallax
            val pitch = trackingOffsetY * 0.40f
            val isImageMode by PresenceEngine.isImageMode.collectAsState()

            if (isImageMode) {
                // Render the 3D realistic Image Face!
                // Glitching chromatic aberration side-shifts
                if (isGlitching && chromaticAberrationOffset != 0f) {
                    Image(
                        painter = painterResource(id = R.drawable.img_cyborg_skull),
                        contentDescription = "Glitch Cyan layer",
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                translationX = -chromaticAberrationOffset
                                rotationX = pitch * 40f
                                rotationY = yaw * 50f
                                cameraDistance = 15f * density
                            },
                        colorFilter = ColorFilter.tint(Color.Cyan.copy(alpha = 0.5f), blendMode = BlendMode.Overlay),
                        contentScale = ContentScale.Fit
                    )
                    Image(
                        painter = painterResource(id = R.drawable.img_cyborg_skull),
                        contentDescription = "Glitch Magenta layer",
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                translationX = chromaticAberrationOffset
                                rotationX = pitch * 40f
                                rotationY = yaw * 50f
                                cameraDistance = 15f * density
                            },
                        colorFilter = ColorFilter.tint(Color.Magenta.copy(alpha = 0.5f), blendMode = BlendMode.Overlay),
                        contentScale = ContentScale.Fit
                    )
                }

                // Persistent stereoscopic offsets for beautiful 3D layered glass feel
                Image(
                    painter = painterResource(id = R.drawable.img_cyborg_skull),
                    contentDescription = "Stereoscopic Cyan layer",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.01f
                            scaleY = 1.01f
                            translationX = -trackingOffsetX * 5f
                            translationY = -trackingOffsetY * 5f
                            rotationX = pitch * 40f
                            rotationY = yaw * 50f
                            cameraDistance = 15f * density
                        },
                    colorFilter = ColorFilter.tint(Color(0xFF00FFEA).copy(alpha = 0.25f), blendMode = BlendMode.Color),
                    contentScale = ContentScale.Fit
                )
                Image(
                    painter = painterResource(id = R.drawable.img_cyborg_skull),
                    contentDescription = "Stereoscopic Magenta layer",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.01f
                            scaleY = 1.01f
                            translationX = trackingOffsetX * 5f
                            translationY = trackingOffsetY * 5f
                            rotationX = pitch * 40f
                            rotationY = yaw * 50f
                            cameraDistance = 15f * density
                        },
                    colorFilter = ColorFilter.tint(Color(0xFFFF1E56).copy(alpha = 0.25f), blendMode = BlendMode.Color),
                    contentScale = ContentScale.Fit
                )

                // Main HD 3D Image Layer
                Image(
                    painter = painterResource(id = R.drawable.img_cyborg_skull),
                    contentDescription = "Realistic 3D Art AI Face",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            rotationX = pitch * 40f
                            rotationY = yaw * 50f
                            cameraDistance = 15f * density
                        },
                    contentScale = ContentScale.Fit
                )

                // High-End Responsive 3D Cyber Overlay over the realistic image!
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            rotationX = pitch * 40f
                            rotationY = yaw * 50f
                            cameraDistance = 15f * density
                        }
                ) {
                    val baseWidth = 400f
                    val baseHeight = 400f
                    val scaleX = size.width / baseWidth
                    val scaleY = size.height / baseHeight
                    val scale = minOf(scaleX, scaleY)

                    // Draw centered inside coordinate space
                    drawContext.canvas.save()
                    val offsetX = (size.width - baseWidth * scale) / 2f
                    val offsetY = (size.height - baseHeight * scale) / 2f
                    drawContext.transform.translate(offsetX, offsetY)
                    drawContext.transform.scale(scale, scale, pivot = Offset(0f, 0f))

                    // Approximate Eye Locations on the center of skull image
                    val finalLeftProj = Offset(155f, 168f)
                    val finalRightProj = Offset(245f, 168f)

                    val glitchPupilScale = if (isGlitching) (if (Random.nextFloat() > 0.5f) 0.3f else 1.3f) else 1f
                    val eyeRadiusL = 16f * pupilSize.coerceAtLeast(0.5f) * glitchPupilScale
                    val eyeRadiusR = 16f * pupilSize.coerceAtLeast(0.5f) * glitchPupilScale

                    val eyeGlowColor = if (isGlitching) {
                        if (Random.nextBoolean()) Color(0xFF00FFFF) else Color(0xFFFFFF00)
                    } else {
                        animatedEyeColor
                    }

                    // Eye Glow Overlays
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(eyeGlowColor.copy(alpha = if (isGlitching) 0.8f else 0.9f), eyeGlowColor.copy(alpha = 0.2f), Color.Transparent),
                            center = finalLeftProj,
                            radius = eyeRadiusL * 2.5f
                        ),
                        radius = eyeRadiusL * 2.5f,
                        center = finalLeftProj
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(eyeGlowColor.copy(alpha = if (isGlitching) 0.6f else 0.7f), eyeGlowColor.copy(alpha = 0.1f), Color.Transparent),
                            center = finalRightProj,
                            radius = eyeRadiusR * 2.2f
                        ),
                        radius = eyeRadiusR * 2.2f,
                        center = finalRightProj
                    )

                    // Tracking pupils
                    val looksParallaxX = yaw * 8f
                    val looksParallaxY = pitch * 8f
                    val leftPupilProj = Offset(finalLeftProj.x + looksParallaxX, finalLeftProj.y + looksParallaxY)
                    val rightPupilProj = Offset(finalRightProj.x + looksParallaxX, finalRightProj.y + looksParallaxY)

                    drawCircle(
                        color = Color.White.copy(alpha = 0.95f * intensity),
                        radius = eyeRadiusL * 0.32f,
                        center = leftPupilProj
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.75f * intensity),
                        radius = eyeRadiusR * 0.32f,
                        center = rightPupilProj
                    )

                    // Reticle dashes rotating
                    val timeSecs = (System.currentTimeMillis() % 100000) / 1000f
                    val ringOffsetAngle = timeSecs * 60f
                    drawCircle(
                        color = eyeGlowColor.copy(alpha = 0.6f),
                        radius = eyeRadiusL * 1.5f,
                        center = finalLeftProj,
                        style = Stroke(
                            width = 2.0f,
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(5f, 10f), ringOffsetAngle)
                        )
                    )
                    drawCircle(
                        color = eyeGlowColor.copy(alpha = 0.5f),
                        radius = eyeRadiusR * 1.5f,
                        center = finalRightProj,
                        style = Stroke(
                            width = 2.0f,
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(5f, 10f), -ringOffsetAngle)
                        )
                    )

                    // HUD Crosshairs
                    val scopeWidthL = eyeRadiusL * 0.9f
                    drawLine(color = eyeGlowColor.copy(alpha = 0.5f), start = Offset(finalLeftProj.x - scopeWidthL, finalLeftProj.y), end = Offset(finalLeftProj.x + scopeWidthL, finalLeftProj.y), strokeWidth = 1.0f)
                    drawLine(color = eyeGlowColor.copy(alpha = 0.5f), start = Offset(finalLeftProj.x, finalLeftProj.y - scopeWidthL), end = Offset(finalLeftProj.x, finalLeftProj.y + scopeWidthL), strokeWidth = 1.0f)
                    val scopeWidthR = eyeRadiusR * 0.9f
                    drawLine(color = eyeGlowColor.copy(alpha = 0.45f), start = Offset(finalRightProj.x - scopeWidthR, finalRightProj.y), end = Offset(finalRightProj.x + scopeWidthR, finalRightProj.y), strokeWidth = 1.0f)
                    drawLine(color = eyeGlowColor.copy(alpha = 0.45f), start = Offset(finalRightProj.x, finalRightProj.y - scopeWidthR), end = Offset(finalRightProj.x, finalRightProj.y + scopeWidthR), strokeWidth = 1.0f)

                    // Vocal / Mouth laser bar synced with voice
                    val showGlitchedMouth = isGlitching || lipSyncPulse > 0f
                    if (showGlitchedMouth) {
                        val currentPulse = if (isGlitching && lipSyncPulse == 0f) {
                            if (Random.nextFloat() > 0.4f) Random.nextFloat() * 0.4f + 0.1f else 0f
                        } else {
                            lipSyncPulse
                        }

                        if (currentPulse > 0f) {
                            val mouthY = 275f
                            val mouthWidth = 80f * (if (isGlitching) (1f + Random.nextFloat() * 0.3f) else 1f)
                            val mouthCenterX = 200f

                            // Radial vocal glow
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf((if (isGlitching) Color.Red else eyeGlowColor).copy(alpha = 0.7f), Color.Transparent),
                                    center = Offset(mouthCenterX, mouthY),
                                    radius = mouthWidth * 0.8f
                                ),
                                radius = mouthWidth * 0.8f,
                                center = Offset(mouthCenterX, mouthY)
                            )

                            // Sine vocal soundwave drawing
                            val wPath = Path().apply {
                                moveTo(mouthCenterX - mouthWidth * 0.5f, mouthY)
                                val steps = 20
                                val stepSize = mouthWidth / steps
                                for (step in 0..steps) {
                                    val sx = (mouthCenterX - mouthWidth * 0.5f) + step * stepSize
                                    val angle = (step.toFloat() / steps.toFloat()) * 4f * kotlin.math.PI
                                    val sy = mouthY + kotlin.math.sin(angle).toFloat() * currentPulse * 18f * (if (isGlitching) 1.5f else 1.0f)
                                    lineTo(sx, sy)
                                }
                            }

                            drawPath(
                                path = wPath,
                                color = if (isGlitching) Color.Red else eyeGlowColor,
                                style = Stroke(width = (2f + currentPulse * 6f), cap = StrokeCap.Round)
                            )
                        }
                    }

                    drawContext.canvas.restore()
                }
            } else {
                // Render the original dynamic 100% Vector Cyber Face Skull (Fallback/Toggle option!)
                
                // Chromatic Aberration / Digital Disruption layers if glitching
                if (isGlitching && chromaticAberrationOffset != 0f) {
                    // Cyan split offset
                    CyberSkullVector(
                        eyeSlant = eyeSlant,
                        mouthCurve = animatedMouthCurve,
                        pupilSize = pupilSize,
                        intensity = intensity,
                        lipSyncPulse = lipSyncPulse,
                        emotion = emotion,
                        primaryColor = Color.Cyan.copy(alpha = 0.5f),
                        auraColor = animatedAuraColor,
                        isGlitching = isGlitching,
                        tintColor = Color.Cyan.copy(alpha = 0.5f),
                        blendMode = BlendMode.Overlay,
                        yaw = yaw,
                        pitch = pitch,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                translationX = -chromaticAberrationOffset
                            }
                    )

                    // Magenta split offset
                    CyberSkullVector(
                        eyeSlant = eyeSlant,
                        mouthCurve = animatedMouthCurve,
                        pupilSize = pupilSize,
                        intensity = intensity,
                        lipSyncPulse = lipSyncPulse,
                        emotion = emotion,
                        primaryColor = Color.Magenta.copy(alpha = 0.5f),
                        auraColor = animatedAuraColor,
                        isGlitching = isGlitching,
                        tintColor = Color.Magenta.copy(alpha = 0.5f),
                        blendMode = BlendMode.Overlay,
                        yaw = yaw,
                        pitch = pitch,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                translationX = chromaticAberrationOffset
                            }
                    )
                }

                // Persistent 3D Stereoscopic split layers (Red/Cyan shadows) for depth illusion
                // Left Cyan dynamic 3D depth shadow (always-on, expands with movement)
                CyberSkullVector(
                    eyeSlant = eyeSlant,
                    mouthCurve = animatedMouthCurve,
                    pupilSize = pupilSize,
                    intensity = intensity,
                    lipSyncPulse = lipSyncPulse,
                    emotion = emotion,
                    primaryColor = Color(0xFF00FFEA).copy(alpha = 0.35f),
                    auraColor = animatedAuraColor,
                    isGlitching = isGlitching,
                    tintColor = Color(0xFF00FFEA).copy(alpha = 0.35f),
                    blendMode = BlendMode.Screen,
                    yaw = yaw + (trackingOffsetX * -0.06f),
                    pitch = pitch + (trackingOffsetY * 0.04f),
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.01f
                            scaleY = 1.01f
                        }
                )

                // Right Red/Magenta dynamic 3D depth shadow (always-on, expands with movement)
                CyberSkullVector(
                    eyeSlant = eyeSlant,
                    mouthCurve = animatedMouthCurve,
                    pupilSize = pupilSize,
                    intensity = intensity,
                    lipSyncPulse = lipSyncPulse,
                    emotion = emotion,
                    primaryColor = Color(0xFFFF1E56).copy(alpha = 0.35f),
                    auraColor = animatedAuraColor,
                    isGlitching = isGlitching,
                    tintColor = Color(0xFFFF1E56).copy(alpha = 0.35f),
                    blendMode = BlendMode.Screen,
                    yaw = yaw - (trackingOffsetX * -0.06f),
                    pitch = pitch - (trackingOffsetY * 0.04f),
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.01f
                            scaleY = 1.01f
                        }
                )

                // Main Dynamic Vector Cyber Face Skull
                CyberSkullVector(
                    eyeSlant = eyeSlant,
                    mouthCurve = animatedMouthCurve,
                    pupilSize = pupilSize,
                    intensity = intensity,
                    lipSyncPulse = lipSyncPulse,
                    emotion = emotion,
                    primaryColor = animatedEyeColor,
                    auraColor = animatedAuraColor,
                    isGlitching = isGlitching,
                    yaw = yaw,
                    pitch = pitch,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Horizontal visual corruption bars / digital memory blocks overlay (screen space glitch)
            if (isGlitching && glitchBars.isNotEmpty()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val baseLeft = 0f
                    glitchBars.forEach { bar ->
                        val topY = size.height * bar.topPercent
                        val h = size.height * bar.heightPercent
                        val w = size.width * bar.widthPercent
                        val offsetX = size.width * bar.offsetPercent
                        
                        drawRect(
                            color = bar.color,
                            topLeft = androidx.compose.ui.geometry.Offset(
                                x = baseLeft + offsetX, 
                                y = topY
                            ),
                            size = androidx.compose.ui.geometry.Size(w, h),
                            blendMode = BlendMode.SrcOver
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CyberSkullVector(
    modifier: Modifier = Modifier,
    eyeSlant: Float,
    mouthCurve: Float,
    pupilSize: Float,
    intensity: Float,
    lipSyncPulse: Float,
    emotion: String,
    primaryColor: Color,
    auraColor: Color,
    isGlitching: Boolean,
    tintColor: Color? = null,
    blendMode: BlendMode = BlendMode.SrcOver,
    yaw: Float = 0f,
    pitch: Float = 0f
) {
    Canvas(modifier = modifier) {
        val baseWidth = 400f
        val baseHeight = 400f
        val scaleX = size.width / baseWidth
        val scaleY = size.height / baseHeight
        val scale = minOf(scaleX, scaleY)

        val strokeColor = tintColor ?: primaryColor.copy(alpha = 0.45f + (intensity * 0.35f))
        val strokeWidth = 2f * scale

        // Scale Canvas centered at origin
        drawContext.canvas.save()
        val offsetX = (size.width - baseWidth * scale) / 2f
        val offsetY = (size.height - baseHeight * scale) / 2f
        drawContext.transform.translate(offsetX, offsetY)
        drawContext.transform.scale(scale, scale, pivot = Offset(0f, 0f))

        // Set up custom colors with depth shading
        val boneColorBright = tintColor ?: Color(0xFFFCFAF2)
        val boneColorMid = tintColor ?: Color(0xFFEDE9DE)
        val boneColorDark = tintColor ?: Color(0xFFC7C1B3)
        val boneColorShadow = tintColor ?: Color(0xFF908A7C)

        val redChassisBright = tintColor ?: Color(0xFFFF2E2E)
        val redChassisDark = tintColor ?: Color(0xFF6B0B0B)
        val silverChassisBright = tintColor ?: Color(0xFFECEFF1)
        val silverChassisDark = tintColor ?: Color(0xFF37474F)

        val muscleBright = tintColor ?: Color(0xFFD32F2F)
        val muscleDark = tintColor ?: Color(0xFF420B0B)

        // Light sources tracking the rotation yaw/pitch (PBR dynamic light highlight simulation!)
        val foreheadGradientCenter = project3D(200f, 100f, 25f, yaw, pitch)
        val foreheadGradientRadius = 160f * getPerspectiveScale(25f, yaw, pitch)
        val foreheadBrush = Brush.radialGradient(
            colors = listOf(boneColorBright, boneColorMid, boneColorDark),
            center = foreheadGradientCenter,
            radius = foreheadGradientRadius
        )

        val cheekLeftProj = project3D(110f, 220f, 15f, yaw, pitch)
        val cheekLeftRad = 65f * getPerspectiveScale(15f, yaw, pitch)
        val cheekLeftBrush = Brush.radialGradient(
            colors = listOf(boneColorBright, boneColorMid, boneColorShadow),
            center = cheekLeftProj,
            radius = cheekLeftRad
        )

        val cheekRightProj = project3D(290f, 220f, 15f, yaw, pitch)
        val cheekRightRad = 65f * getPerspectiveScale(15f, yaw, pitch)
        val cheekRightBrush = Brush.radialGradient(
            colors = listOf(boneColorBright, boneColorMid, boneColorShadow),
            center = cheekRightProj,
            radius = cheekRightRad
        )

        val chinProj = project3D(200f, 320f, 30f, yaw, pitch)
        val chinRad = 80f * getPerspectiveScale(30f, yaw, pitch)
        val chinBrush = Brush.radialGradient(
            colors = listOf(boneColorBright, boneColorMid, boneColorDark),
            center = chinProj,
            radius = chinRad
        )

        // 1. BACK DEEP CAVITY / SHADOW SPACE
        val backCenter = project3D(200f, 200f, 0f, yaw, pitch)
        val backRadius = 175f * getPerspectiveScale(0f, yaw, pitch)
        drawCircle(
            color = Color(0xFF040608),
            radius = backRadius,
            center = backCenter,
            blendMode = blendMode
        )

        // 2. METAL SPINAL COLUMN (Vertebrae discs stacked deep at z = -80f)
        for (i in 0..5) {
            val spineY = 270f + i * 36f
            val vScale = if (spineY > 370f) 1.25f else 1.0f
            val vWidth = 48f * vScale
            val vHeight = 22f
            
            val zSpine = -80f
            val p1 = project3D(200f - vWidth / 2f, spineY, zSpine, yaw, pitch)
            val p2 = project3D(200f + vWidth / 2f, spineY, zSpine, yaw, pitch)
            val p3 = project3D(200f + vWidth / 2f, spineY + vHeight, zSpine, yaw, pitch)
            val p4 = project3D(200f - vWidth / 2f, spineY + vHeight, zSpine, yaw, pitch)
            
            val spineQuad = Path().apply {
                moveTo(p1.x, p1.y)
                lineTo(p2.x, p2.y)
                lineTo(p3.x, p3.y)
                lineTo(p4.x, p4.y)
                close()
            }
            
            drawPath(
                path = spineQuad,
                brush = Brush.linearGradient(
                    colors = listOf(silverChassisBright, silverChassisDark),
                    start = p1,
                    end = p3
                ),
                style = Fill,
                blendMode = blendMode
            )
            drawPath(
                path = spineQuad,
                color = strokeColor,
                style = Stroke(width = strokeWidth * 0.6f),
                blendMode = blendMode
            )

            // Vertebra horizontal dividing line
            val pLine1 = project3D(200f - vWidth / 2f, spineY + vHeight - 4f, zSpine, yaw, pitch)
            val pLine2 = project3D(200f + vWidth / 2f, spineY + vHeight - 4f, zSpine, yaw, pitch)
            drawLine(
                color = Color(0xFF07090C),
                start = pLine1,
                end = pLine2,
                strokeWidth = 2f,
                blendMode = blendMode
            )
        }

        // 3. STRIATED RED MUSCULAR FIBERS/CABLES
        val jawOpenOffset = lipSyncPulse * 30f // Speech jaw articulation travel
        
        val musclePins = listOf(
            // Left Group
            Pair(Offset(102f, 240f), Offset(90f, 410f)),
            Pair(Offset(120f, 252f), Offset(115f, 420f)),
            Pair(Offset(138f, 260f), Offset(140f, 430f)),
            Pair(Offset(156f, 265f), Offset(165f, 440f)),
            
            // Right Group
            Pair(Offset(298f, 240f), Offset(310f, 410f)),
            Pair(Offset(280f, 252f), Offset(285f, 420f)),
            Pair(Offset(262f, 260f), Offset(260f, 430f)),
            Pair(Offset(244f, 265f), Offset(235f, 440f))
        )

        musclePins.forEach { (start, end) ->
            val muscleStartY = start.y + (if (start.y > 245f) jawOpenOffset * 0.45f else 0f)
            val pStart = project3D(start.x, muscleStartY, 10f, yaw, pitch)
            val pEnd = project3D(end.x, end.y, 25f, yaw, pitch)
            
            val ctrlY = (muscleStartY + end.y) / 2f
            val ctrlXOffset = -12f * (if (start.x < 200f) 1f else -1f)
            
            val pCtrl1 = project3D((start.x + end.x) / 2f + ctrlXOffset, ctrlY, 15f, yaw, pitch)
            val pCtrl2 = project3D((start.x + end.x) / 2f + ctrlXOffset * 0.3f, end.y - 12f, 20f, yaw, pitch)

            val mPath = Path().apply {
                moveTo(pStart.x, pStart.y)
                cubicTo(pCtrl1.x, pCtrl1.y, pCtrl2.x, pCtrl2.y, pEnd.x, pEnd.y)
            }
            
            // Thick border
            drawPath(
                path = mPath,
                color = muscleDark,
                style = Stroke(width = 11f * scale, cap = StrokeCap.Round, join = StrokeJoin.Round),
                blendMode = blendMode
            )
            // Vibrant red core
            drawPath(
                path = mPath,
                color = muscleBright,
                style = Stroke(width = 4.5f * scale, cap = StrokeCap.Round, join = StrokeJoin.Round),
                blendMode = blendMode
            )
            // Glowing neon center thread
            drawPath(
                path = mPath,
                color = (tintColor ?: Color.White).copy(alpha = 0.5f),
                style = Stroke(width = 1.0f * scale, cap = StrokeCap.Round),
                blendMode = blendMode
            )
        }

        // 4. LOWER THORAX (Bone Clavicles & Ribcage)
        val clavicleLeft3D = Path().apply {
            moveTo3D(200f, 385f, 35f, yaw, pitch)
            cubicTo3D(160f, 380f, 30f, 110f, 375f, 15f, 75f, 395f, 0f, yaw, pitch)
            cubicTo3D(50f, 410f, -10f, 35f, 410f, -15f, 20f, 428f, -20f, yaw, pitch)
            lineTo3D(32f, 438f, -20f, yaw, pitch)
            cubicTo3D(50f, 420f, -10f, 70f, 422f, 5f, 105f, 405f, 15f, yaw, pitch)
            cubicTo3D(140f, 395f, 25f, 175f, 395f, 30f, 200f, 400f, 30f, yaw, pitch)
            close()
        }
        val clavicleRight3D = Path().apply {
            moveTo3D(200f, 385f, 35f, yaw, pitch)
            cubicTo3D(240f, 380f, 30f, 290f, 375f, 15f, 325f, 395f, 0f, yaw, pitch)
            cubicTo3D(350f, 410f, -10f, 365f, 410f, -15f, 380f, 428f, -20f, yaw, pitch)
            lineTo3D(368f, 438f, -20f, yaw, pitch)
            cubicTo3D(350f, 420f, -10f, 330f, 422f, 5f, 295f, 405f, 15f, yaw, pitch)
            cubicTo3D(260f, 395f, 25f, 225f, 395f, 30f, 200f, 400f, 30f, yaw, pitch)
            close()
        }
        val ribsBrush = tintColor?.let { Brush.linearGradient(colors = listOf(it, it), start = Offset(200f, 380f), end = Offset(200f, 480f)) }
            ?: Brush.linearGradient(
                colors = listOf(boneColorMid, boneColorDark),
                start = Offset(200f, 380f),
                end = Offset(200f, 480f)
            )

        drawPath(path = clavicleLeft3D, brush = ribsBrush, style = Fill, blendMode = blendMode)
        drawPath(path = clavicleLeft3D, color = strokeColor, style = Stroke(width = 1.5f * scale), blendMode = blendMode)
        drawPath(path = clavicleRight3D, brush = ribsBrush, style = Fill, blendMode = blendMode)
        drawPath(path = clavicleRight3D, color = strokeColor, style = Stroke(width = 1.5f * scale), blendMode = blendMode)

        // Rib sweeps caging outwards
        val rib1Left3D = Path().apply {
            moveTo3D(186f, 420f, 25f, yaw, pitch)
            cubicTo3D(130f, 424f, 20f, 80f, 438f, 5f, 45f, 474f, -10f, yaw, pitch)
            lineTo3D(50f, 484f, -10f, yaw, pitch)
            cubicTo3D(90f, 452f, 5f, 135f, 436f, 15f, 186f, 434f, 25f, yaw, pitch)
            close()
        }
        val rib1Right3D = Path().apply {
            moveTo3D(214f, 420f, 25f, yaw, pitch)
            cubicTo3D(270f, 424f, 20f, 320f, 438f, 5f, 355f, 474f, -10f, yaw, pitch)
            lineTo3D(350f, 484f, -10f, yaw, pitch)
            cubicTo3D(310f, 452f, 5f, 265f, 436f, 15f, 214f, 434f, 25f, yaw, pitch)
            close()
        }
        drawPath(path = rib1Left3D, brush = ribsBrush, style = Fill, blendMode = blendMode)
        drawPath(path = rib1Left3D, color = strokeColor, style = Stroke(width = 1.2f * scale), blendMode = blendMode)
        drawPath(path = rib1Right3D, brush = ribsBrush, style = Fill, blendMode = blendMode)
        drawPath(path = rib1Right3D, color = strokeColor, style = Stroke(width = 1.2f * scale), blendMode = blendMode)

        // Center Sternum plate
        val sternumPath3D = Path().apply {
            moveTo3D(192f, 395f, 35f, yaw, pitch)
            lineTo3D(208f, 395f, 35f, yaw, pitch)
            lineTo3D(213f, 480f, 25f, yaw, pitch)
            lineTo3D(187f, 480f, 25f, yaw, pitch)
            close()
        }
        drawPath(path = sternumPath3D, brush = ribsBrush, style = Fill, blendMode = blendMode)
        drawPath(path = sternumPath3D, color = strokeColor, style = Stroke(width = 1.5f * scale), blendMode = blendMode)

        // 5. UPPER BONE COWL (Forehead Dome structure)
        val upperSkullPath3D = Path().apply {
            moveTo3D(200f, 40f, 30f, yaw, pitch) // top crown center
            
            // Temporal right sweep
            cubicTo3D(265f, 40f, 20f, 310f, 75f, -10f, 315f, 130f, -25f, yaw, pitch)
            lineTo3D(320f, 155f, -30f, yaw, pitch) // Temporal joint notch start
            lineTo3D(295f, 155f, -15f, yaw, pitch)
            lineTo3D(295f, 185f, -10f, yaw, pitch)
            
            // Cheekbone zygomatic plate sweep right
            cubicTo3D(310f, 210f, 5f, 292f, 246f, 15f, 265f, 248f, 20f, yaw, pitch)
            lineTo3D(268f, 258f, 22f, yaw, pitch) // gum limit
            
            // Symmetrical sweep back around upper lip
            lineTo3D(132f, 258f, 22f, yaw, pitch)
            lineTo3D(135f, 248f, 20f, yaw, pitch)
            cubicTo3D(108f, 246f, 15f, 90f, 210f, 5f, 105f, 185f, -10f, yaw, pitch)
            lineTo3D(105f, 155f, -15f, yaw, pitch)
            lineTo3D(80f, 155f, -30f, yaw, pitch) // Temporal joint notch left
            lineTo3D(85f, 130f, -25f, yaw, pitch)
            
            // Temporal left sweep
            cubicTo3D(90f, 75f, -10f, 135f, 40f, 20f, 200f, 40f, 30f, yaw, pitch)
            close()
        }

        drawPath(path = upperSkullPath3D, brush = foreheadBrush, style = Fill, blendMode = blendMode)
        drawPath(path = upperSkullPath3D, color = strokeColor, style = Stroke(width = 3.5f * scale, join = StrokeJoin.Round), blendMode = blendMode)

        // Forehead crest crease line
        val crest1 = project3D(200f, 40f, 30f, yaw, pitch)
        val crest2 = project3D(200f, 130f, 33f, yaw, pitch)
        drawLine(
            color = strokeColor.copy(alpha = 0.25f),
            start = crest1,
            end = crest2,
            strokeWidth = 2f,
            blendMode = blendMode
        )

        // 6. CYBERNETIC EAR MODULES (Temporal temples pivots)
        val earCenterLeft = project3D(73f, 142f, -30f, yaw, pitch)
        val earCenterRight = project3D(327f, 142f, -30f, yaw, pitch)
        
        listOf(earCenterLeft, earCenterRight).forEach { center ->
            val factor = getPerspectiveScale(-30f, yaw, pitch)
            // Outer Red casing ring
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(redChassisBright, redChassisDark),
                    center = center,
                    radius = 21f * factor
                ),
                radius = 21f * factor,
                center = center,
                blendMode = blendMode
            )
            // Silver mechanical cylinder core
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(silverChassisBright, silverChassisDark),
                    start = Offset(center.x - 10f * factor, center.y - 10f * factor),
                    end = Offset(center.x + 10f * factor, center.y + 10f * factor)
                ),
                radius = 11f * factor,
                center = center,
                blendMode = blendMode
            )
            // Central cap
            drawCircle(
                color = Color(0xFF101216),
                radius = 5f * factor,
                center = center,
                blendMode = blendMode
            )
        }

        // 7. ORBITAL CAVITIES (Deep shadowed sunken sockets)
        val slant = eyeSlant * 16f
        val eyeSocketLeft3D = Path().apply {
            moveTo3D(112f, 144f - slant * 0.4f, 15f, yaw, pitch)
            lineTo3D(184f, 154f + slant * 0.8f, 30f, yaw, pitch)
            lineTo3D(178f, 198f, 30f, yaw, pitch)
            lineTo3D(124f, 194f, 15f, yaw, pitch)
            close()
        }
        val eyeSocketRight3D = Path().apply {
            moveTo3D(288f, 144f - slant * 0.4f, 15f, yaw, pitch)
            lineTo3D(216f, 154f + slant * 0.8f, 30f, yaw, pitch)
            lineTo3D(222f, 198f, 30f, yaw, pitch)
            lineTo3D(276f, 194f, 15f, yaw, pitch)
            close()
        }
        
        drawPath(path = eyeSocketLeft3D, color = Color(0xFF040608), style = Fill, blendMode = blendMode)
        drawPath(path = eyeSocketLeft3D, color = strokeColor, style = Stroke(width = 2f * scale, join = StrokeJoin.Round), blendMode = blendMode)
        drawPath(path = eyeSocketRight3D, color = Color(0xFF040608), style = Fill, blendMode = blendMode)
        drawPath(path = eyeSocketRight3D, color = strokeColor, style = Stroke(width = 2f * scale, join = StrokeJoin.Round), blendMode = blendMode)

        // Eyebrow arch ridges
        val archPath3D = Path().apply {
            moveTo3D(110f, 140f, 22f, yaw, pitch)
            quadraticTo3D(148f, 126f, 25f, 200f, 142f, 28f, yaw, pitch)
            quadraticTo3D(252f, 126f, 25f, 290f, 140f, 22f, yaw, pitch)
        }
        drawPath(
            path = archPath3D,
            color = (tintColor ?: Color(0xFFD3CDBE)).copy(alpha = 0.5f),
            style = Stroke(width = 4.5f * scale),
            blendMode = blendMode
        )

        // 8. COAL BLACK NOSE CAVITY (Upside-down heart)
        val noseCavity3D = Path().apply {
            moveTo3D(200f, 192f, 35f, yaw, pitch)
            cubicTo3D(209f, 193f, 35f, 213f, 214f, 30f, 200f, 220f, 35f, yaw, pitch)
            cubicTo3D(187f, 214f, 30f, 191f, 193f, 35f, 200f, 192f, 35f, yaw, pitch)
            close()
        }
        drawPath(path = noseCavity3D, color = Color(0xFF040608), style = Fill, blendMode = blendMode)
        drawPath(path = noseCavity3D, color = strokeColor, style = Stroke(width = 1.5f * scale), blendMode = blendMode)

        // Forehead nose-bridge accent vertical strip
        val noseBridgeStart = project3D(200f, 142f, 28f, yaw, pitch)
        val noseBridgeEnd = project3D(200f, 193f, 35f, yaw, pitch)
        drawLine(
            color = strokeColor.copy(alpha = 0.35f),
            start = noseBridgeStart,
            end = noseBridgeEnd,
            strokeWidth = 1.8f * scale,
            blendMode = blendMode
        )

        // 9. CYBERNETIC FLOATING EYES (Concentric HUD scopes projected in orbits)
        val finalLeftProj = project3D(148f, 171f, 25f, yaw, pitch)
        val finalRightProj = project3D(252f, 171f, 25f, yaw, pitch)
        
        val factorL = getPerspectiveScale(25f, yaw, pitch)
        val factorR = getPerspectiveScale(25f, yaw, pitch)
        
        val glitchPupilScale = if (isGlitching) (if (Random.nextFloat() > 0.5f) 0.3f else 1.3f) else 1f
        val eyeRadiusL = 20f * pupilSize.coerceAtLeast(0.5f) * glitchPupilScale * factorL
        val eyeRadiusR = 20f * pupilSize.coerceAtLeast(0.5f) * glitchPupilScale * factorR

        val eyeGlowColor = if (isGlitching) {
            if (Random.nextBoolean()) Color(0xFF00FFFF) else Color(0xFFFFFF00)
        } else {
            primaryColor
        }

        // Left Eye Glow Overlay
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(eyeGlowColor.copy(alpha = if (isGlitching) 0.8f else 0.9f), eyeGlowColor.copy(alpha = 0.2f), Color.Transparent),
                center = finalLeftProj,
                radius = eyeRadiusL * 2.5f
            ),
            radius = eyeRadiusL * 2.5f,
            center = finalLeftProj,
            blendMode = blendMode
        )

        // Right Eye Glow Overlay
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(eyeGlowColor.copy(alpha = if (isGlitching) 0.6f else 0.7f), eyeGlowColor.copy(alpha = 0.1f), Color.Transparent),
                center = finalRightProj,
                radius = eyeRadiusR * 2.2f
            ),
            radius = eyeRadiusR * 2.2f,
            center = finalRightProj,
            blendMode = blendMode
        )

        // Intense central pupils with look-offset parallax shift!
        val looksParallaxX = yaw * 10f
        val looksParallaxY = pitch * 10f
        
        val leftPupilProj = Offset(finalLeftProj.x + looksParallaxX, finalLeftProj.y + looksParallaxY)
        val rightPupilProj = Offset(finalRightProj.x + looksParallaxX, finalRightProj.y + looksParallaxY)

        drawCircle(
            color = Color.White.copy(alpha = 0.95f * intensity),
            radius = eyeRadiusL * 0.28f,
            center = leftPupilProj,
            blendMode = blendMode
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.75f * intensity),
            radius = eyeRadiusR * 0.28f,
            center = rightPupilProj,
            blendMode = blendMode
        )

        // Concentric target focusing rings (terminator dots rotating with system time)
        val timeSecs = (System.currentTimeMillis() % 100000) / 1000f
        val ringOffsetAngle = timeSecs * 60f
        drawCircle(
            color = eyeGlowColor.copy(alpha = 0.5f),
            radius = eyeRadiusL * 1.5f,
            center = finalLeftProj,
            style = Stroke(
                width = 2.5f * scale,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 12f), ringOffsetAngle)
            ),
            blendMode = blendMode
        )
        drawCircle(
            color = eyeGlowColor.copy(alpha = 0.4f),
            radius = eyeRadiusR * 1.5f,
            center = finalRightProj,
            style = Stroke(
                width = 2.5f * scale,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 12f), -ringOffsetAngle)
            ),
            blendMode = blendMode
        )

        // Crosshairs of targeting HUD modules
        val scopeWidthL = eyeRadiusL * 0.9f
        drawLine(color = eyeGlowColor.copy(alpha = 0.4f), start = Offset(finalLeftProj.x - scopeWidthL, finalLeftProj.y), end = Offset(finalLeftProj.x + scopeWidthL, finalLeftProj.y), strokeWidth = 1.5f * scale, blendMode = blendMode)
        drawLine(color = eyeGlowColor.copy(alpha = 0.4f), start = Offset(finalLeftProj.x, finalLeftProj.y - scopeWidthL), end = Offset(finalLeftProj.x, finalLeftProj.y + scopeWidthL), strokeWidth = 1.5f * scale, blendMode = blendMode)
        
        val scopeWidthR = eyeRadiusR * 0.9f
        drawLine(color = eyeGlowColor.copy(alpha = 0.35f), start = Offset(finalRightProj.x - scopeWidthR, finalRightProj.y), end = Offset(finalRightProj.x + scopeWidthR, finalRightProj.y), strokeWidth = 1.5f * scale, blendMode = blendMode)
        drawLine(color = eyeGlowColor.copy(alpha = 0.35f), start = Offset(finalRightProj.x, finalRightProj.y - scopeWidthR), end = Offset(finalRightProj.x, finalRightProj.y + scopeWidthR), strokeWidth = 1.5f * scale, blendMode = blendMode)

        // 10. ARTICULATING LOWER JAWBONE (Chin jaw cylinder pivot)
        val lowerJawPath3D = Path().apply {
            // Left cheek bone hinge connection
            moveTo3D(110f, 248f + jawOpenOffset, 15f, yaw, pitch)
            lineTo3D(290f, 248f + jawOpenOffset, 15f, yaw, pitch)
            
            // Side sweep mandibular ramus
            lineTo3D(276f, 308f + jawOpenOffset, 20f, yaw, pitch)
            cubicTo3D(
                255f, 354f + jawOpenOffset, 25f,
                222f, 358f + jawOpenOffset, 30f,
                200f, 358f + jawOpenOffset, 35f,
                yaw, pitch
            )
            cubicTo3D(
                178f, 354f + jawOpenOffset, 25f,
                145f, 358f + jawOpenOffset, 30f,
                124f, 308f + jawOpenOffset, 20f,
                yaw, pitch
            )
            lineTo3D(110f, 248f + jawOpenOffset, 15f, yaw, pitch)
            close()
        }

        drawPath(path = lowerJawPath3D, brush = chinBrush, style = Fill, blendMode = blendMode)
        drawPath(path = lowerJawPath3D, color = strokeColor, style = Stroke(width = 3.5f * scale, join = StrokeJoin.Round), blendMode = blendMode)

        // Mental protuberance panel seam (Chin details)
        val chinPanelLines3D = Path().apply {
            moveTo3D(180f, 335f + jawOpenOffset, 28f, yaw, pitch)
            cubicTo3D(
                190f, 345f + jawOpenOffset, 30f,
                210f, 345f + jawOpenOffset, 30f,
                220f, 335f + jawOpenOffset, 28f,
                yaw, pitch
            )
            moveTo3D(200f, 318f + jawOpenOffset, 32f, yaw, pitch)
            lineTo3D(200f, 356f + jawOpenOffset, 34f, yaw, pitch)
        }
        drawPath(path = chinPanelLines3D, color = strokeColor.copy(alpha = 0.45f), style = Stroke(width = 1.5f * scale), blendMode = blendMode)

        // 11. HYDRAULIC SPEAKING CYLINDERS (Bilateral sliding silver chassis pistons)
        val lCylStart = project3D(105f, 205f, -10f, yaw, pitch)
        val lCylEnd = project3D(122f, 292f + jawOpenOffset, 18f, yaw, pitch)
        val rCylStart = project3D(295f, 205f, -10f, yaw, pitch)
        val rCylEnd = project3D(278f, 292f + jawOpenOffset, 18f, yaw, pitch)

        listOf(Pair(lCylStart, lCylEnd), Pair(rCylStart, rCylEnd)).forEach { (start, end) ->
            // Cylinder sleeve/sheath (Dark heavy steel rod)
            drawLine(
                color = silverChassisDark,
                start = start,
                end = end,
                strokeWidth = 6.5f * scale,
                cap = StrokeCap.Round,
                blendMode = blendMode
            )
            // Shining inner chrome shaft core (Slides dynamically!)
            drawLine(
                color = silverChassisBright,
                start = start,
                end = end,
                strokeWidth = 2.5f * scale,
                cap = StrokeCap.Round,
                blendMode = blendMode
            )
        }

        // 12. BONE TEETH GRATES (Curved 3D dental arch configuration!)
        val isScaryTeeth = emotion.lowercase() == "furious" || emotion.lowercase() == "malicious" || emotion.lowercase() == "vengeance" || emotion.lowercase() == "wrath" || intensity > 0.65f
        
        // Upper Teeth Row (Z = curved arch)
        for (i in 0 until 8) {
            val tWidth = 13f
            val tX = 200f + (i - 4) * 17f + 2f
            val distCenter = kotlin.math.abs(tX - 200f)
            val toothZ = 30f - (distCenter * distCenter * 0.0035f)

            val p1 = project3D(tX, 258f, toothZ, yaw, pitch)
            val p2 = project3D(tX + tWidth, 258f, toothZ, yaw, pitch)

            val toothPath3D = Path().apply {
                if (isScaryTeeth) {
                    moveTo(p1.x, p1.y)
                    lineTo(p2.x, p2.y)
                    val dropLength = if (i == 3 || i == 4) 8f else (if (i == 1 || i == 6) 3f else 1f)
                    val pTip = project3D(tX + tWidth / 2f, 271f + dropLength, toothZ + 2f, yaw, pitch)
                    lineTo(pTip.x, pTip.y)
                    close()
                } else {
                    val p3 = project3D(tX + tWidth, 270f, toothZ, yaw, pitch)
                    val p4 = project3D(tX, 270f, toothZ, yaw, pitch)
                    moveTo(p1.x, p1.y)
                    lineTo(p2.x, p2.y)
                    lineTo(p3.x, p3.y)
                    lineTo(p4.x, p4.y)
                    close()
                }
            }
            drawPath(
                path = toothPath3D,
                color = if (isScaryTeeth) Color(0xFFF7F5F0) else Color(0xFFDEDCD3),
                style = Fill,
                blendMode = blendMode
            )
            drawPath(
                path = toothPath3D,
                color = strokeColor.copy(alpha = 0.5f),
                style = Stroke(width = 1.0f * scale),
                blendMode = blendMode
            )
        }

        // Lower Teeth Row (Attached to descending articulating jaw platform, Z = curved arch)
        for (i in 0 until 8) {
            val tWidth = 13f
            val tX = 200f + (i - 4) * 17f + 2f
            val distCenter = kotlin.math.abs(tX - 200f)
            val toothZ = 30f - (distCenter * distCenter * 0.0035f)
            val baseLowerY = 273f + jawOpenOffset

            val p1 = project3D(tX, baseLowerY, toothZ, yaw, pitch)
            val p2 = project3D(tX + tWidth, baseLowerY, toothZ, yaw, pitch)

            val toothPath3D = Path().apply {
                if (isScaryTeeth) {
                    val pBase1 = project3D(tX, baseLowerY + 14f, toothZ, yaw, pitch)
                    val pBase2 = project3D(tX + tWidth, baseLowerY + 14f, toothZ, yaw, pitch)
                    moveTo(pBase1.x, pBase1.y)
                    lineTo(pBase2.x, pBase2.y)
                    val liftLength = if (i == 2 || i == 5) 6f else 1f
                    val pTip = project3D(tX + tWidth / 2f, baseLowerY - liftLength, toothZ + 2f, yaw, pitch)
                    lineTo(pTip.x, pTip.y)
                    close()
                } else {
                    val p3 = project3D(tX + tWidth, baseLowerY + 12f, toothZ, yaw, pitch)
                    val p4 = project3D(tX, baseLowerY + 12f, toothZ, yaw, pitch)
                    moveTo(p1.x, p1.y)
                    lineTo(p2.x, p2.y)
                    lineTo(p3.x, p3.y)
                    lineTo(p4.x, p4.y)
                    close()
                }
            }
            drawPath(
                path = toothPath3D,
                color = if (isScaryTeeth) Color(0xFFEDE9DE) else Color(0xFFC7C1B3),
                style = Fill,
                blendMode = blendMode
            )
            drawPath(
                path = toothPath3D,
                color = strokeColor.copy(alpha = 0.5f),
                style = Stroke(width = 1.0f * scale),
                blendMode = blendMode
            )
        }

        // 13. SPEECH MOUTH (Glowing dynamic vocal vector wave)
        val showGlitchedMouth = isGlitching || lipSyncPulse > 0f
        if (showGlitchedMouth) {
            val currentPulse = if (isGlitching && lipSyncPulse == 0f) {
                if (Random.nextFloat() > 0.4f) Random.nextFloat() * 0.4f + 0.1f else 0f
            } else {
                lipSyncPulse
            }
            
            if (currentPulse > 0f) {
                val mouthY = 271f + jawOpenOffset * 0.5f
                val mouthWidth = 88f * (if (isGlitching) (1f + Random.nextFloat() * 0.4f) else 1f)
                val mouthCenterX = 200f
                
                val pCenter = project3D(mouthCenterX, mouthY, 32f, yaw, pitch)
                val pScale = getPerspectiveScale(32f, yaw, pitch)
                
                if (isGlitching) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.Red.copy(alpha = 0.8f), Color.Transparent),
                            center = pCenter,
                            radius = mouthWidth * 1.2f * pScale
                        ),
                        radius = mouthWidth * 1.2f * pScale,
                        center = pCenter,
                        blendMode = blendMode
                    )
                } else {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(primaryColor.copy(alpha = 0.8f), Color.Transparent),
                            center = pCenter,
                            radius = mouthWidth * pScale
                        ),
                        radius = mouthWidth * pScale,
                        center = pCenter,
                        blendMode = blendMode
                    )
                }
                
                val mouthPath3D = Path().apply {
                    if (isGlitching && Random.nextFloat() > 0.6f) {
                        // Jagged distorted feedback
                        moveTo3D(mouthCenterX - mouthWidth * 0.4f, mouthY, 32f, yaw, pitch)
                        lineTo3D(mouthCenterX - mouthWidth * 0.2f, mouthY - currentPulse * 15f, 32f, yaw, pitch)
                        lineTo3D(mouthCenterX, mouthY + currentPulse * 20f, 32f, yaw, pitch)
                        lineTo3D(mouthCenterX + mouthWidth * 0.2f, mouthY - currentPulse * 15f, 32f, yaw, pitch)
                        lineTo3D(mouthCenterX + mouthWidth * 0.4f, mouthY, 32f, yaw, pitch)
                    } else {
                        moveTo3D(mouthCenterX - mouthWidth * 0.4f, mouthY, 32f, yaw, pitch)
                        quadraticTo3D(mouthCenterX, mouthY + (currentPulse * mouthCurve), 32f, mouthCenterX + mouthWidth * 0.4f, mouthY, 32f, yaw, pitch)
                    }
                }
                drawPath(
                    path = mouthPath3D,
                    color = if (isGlitching) Color.Red else primaryColor,
                    style = Stroke(width = (4f + currentPulse * (if (isGlitching) 14f else 8f)) * scale),
                    blendMode = blendMode
                )
            }
        }

        drawContext.canvas.restore()
    }
}


