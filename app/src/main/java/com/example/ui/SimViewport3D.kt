package com.example.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.SurveyorItemEntity
import com.example.sim.HardcodedAssetLibrary
import com.example.sim.PooledParticle
import com.example.sim.SignalAspect
import kotlin.math.cos
import kotlin.math.sin

enum class CameraViewMode(val label: String) {
    CAB_VIEW("Cab View"),
    CHASE_CAM("Chase Cam"),
    WEBGL_SINGLE_FILE("WebGL index.html")
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SimViewport3D(
    positionMeters: Double,
    speedMps: Double,
    gradientPercent: Double,
    curveDeg: Double,
    throttleNotch: Int,
    cameraMode: CameraViewMode,
    locoIndex: Int,
    freightCarIndex: Int,
    freightCarCount: Int,
    nextSignalAspect: SignalAspect,
    distanceToNextSignal: Double,
    distanceToNextStation: Double,
    particles: Array<PooledParticle>,
    reducedMotion: Boolean,
    customLiveryColor: Color?,
    isSurveyorMode: Boolean,
    surveyorItems: List<SurveyorItemEntity>,
    onSurveyorGridTap: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    if (cameraMode == CameraViewMode.WEBGL_SINGLE_FILE && !isSurveyorMode) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = WebViewClient()
                    loadUrl("file:///android_asset/index.html")
                }
            },
            modifier = modifier
                .fillMaxSize()
                .testTag("webgl_single_file_webview")
        )
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F17))
            .pointerInput(isSurveyorMode) {
                if (isSurveyorMode) {
                    detectTapGestures { offset ->
                        val normX = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0.05f, 0.95f)
                        val normZ = (offset.y / size.height.coerceAtLeast(1)).coerceIn(0.05f, 0.95f)
                        onSurveyorGridTap(normX * 100f, normZ * 100f)
                    }
                }
            }
            .testTag("sim_viewport_canvas")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (isSurveyorMode) {
                drawSurveyorGrid(surveyorItems)
            } else {
                draw3DTrainWorld(
                    positionMeters = positionMeters,
                    speedMps = speedMps,
                    gradientPercent = gradientPercent,
                    curveDeg = curveDeg,
                    throttleNotch = throttleNotch,
                    cameraMode = cameraMode,
                    locoIndex = locoIndex,
                    freightCarIndex = freightCarIndex,
                    freightCarCount = freightCarCount,
                    nextSignalAspect = nextSignalAspect,
                    distanceToNextSignal = distanceToNextSignal,
                    distanceToNextStation = distanceToNextStation,
                    particles = particles,
                    reducedMotion = reducedMotion,
                    customLiveryColor = customLiveryColor
                )
            }
        }
    }
}

private fun DrawScope.drawSurveyorGrid(surveyorItems: List<SurveyorItemEntity>) {
    val w = size.width
    val h = size.height

    // Blueprint background
    drawRect(color = Color(0xFF0A121E), size = size)

    // 5m Grid Lines
    val cols = 20
    val rows = 20
    val cellW = w / cols
    val cellH = h / rows
    for (c in 0..cols) {
        val x = c * cellW
        drawLine(
            color = if (c % 5 == 0) Color(0xFF2E3B52) else Color(0xFF162030),
            start = Offset(x, 0f),
            end = Offset(x, h),
            strokeWidth = if (c % 5 == 0) 2f else 1f
        )
    }
    for (r in 0..rows) {
        val y = r * cellH
        drawLine(
            color = if (r % 5 == 0) Color(0xFF2E3B52) else Color(0xFF162030),
            start = Offset(0f, y),
            end = Offset(w, y),
            strokeWidth = if (r % 5 == 0) 2f else 1f
        )
    }

    // Default main line spline + user placed track nodes
    val trackNodes = surveyorItems.filter { it.isTrackSplineNode }
    if (trackNodes.size >= 2) {
        for (i in 0 until trackNodes.size - 1) {
            val a = trackNodes[i]
            val b = trackNodes[i + 1]
            val p1 = Offset((a.gridX / 100f) * w, (a.gridZ / 100f) * h)
            val p2 = Offset((b.gridX / 100f) * w, (b.gridZ / 100f) * h)
            drawLine(
                color = Color(0xFF64748B),
                start = p1,
                end = p2,
                strokeWidth = 12f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFFF59E0B),
                start = p1,
                end = p2,
                strokeWidth = 4f,
                cap = StrokeCap.Round
            )
        }
    } else {
        // Baseline reference track loop
        drawLine(
            color = Color(0xFF475569),
            start = Offset(w * 0.15f, h * 0.85f),
            end = Offset(w * 0.85f, h * 0.15f),
            strokeWidth = 10f
        )
    }

    // Draw placed nodes & scenery items
    for (i in surveyorItems.indices) {
        val item = surveyorItems[i]
        val px = (item.gridX / 100f) * w
        val py = (item.gridZ / 100f) * h
        if (item.isTrackSplineNode) {
            drawCircle(color = Color(0xFFF59E0B), radius = 10f, center = Offset(px, py))
            drawCircle(color = Color(0xFF0B0F17), radius = 4f, center = Offset(px, py))
        } else {
            val scSpec = HardcodedAssetLibrary.sceneryItems.firstOrNull { it.id == item.assetId }
            val col = scSpec?.baseColor ?: Color(0xFF10B981)
            drawRect(
                color = col,
                topLeft = Offset(px - 11f, py - 11f),
                size = Size(22f, 22f)
            )
            // Orientation indicator (5-deg snapped)
            val rad = Math.toRadians(item.rotationDeg.toDouble())
            val dirX = px + cos(rad).toFloat() * 18f
            val dirY = py + sin(rad).toFloat() * 18f
            drawLine(
                color = Color.White,
                start = Offset(px, py),
                end = Offset(dirX, dirY),
                strokeWidth = 2.5f
            )
        }
    }
}

private fun DrawScope.draw3DTrainWorld(
    positionMeters: Double,
    speedMps: Double,
    gradientPercent: Double,
    curveDeg: Double,
    throttleNotch: Int,
    cameraMode: CameraViewMode,
    locoIndex: Int,
    freightCarIndex: Int,
    freightCarCount: Int,
    nextSignalAspect: SignalAspect,
    distanceToNextSignal: Double,
    distanceToNextStation: Double,
    particles: Array<PooledParticle>,
    reducedMotion: Boolean,
    customLiveryColor: Color?
) {
    val w = size.width
    val h = size.height
    val pitchShift = (gradientPercent * 8.0).toFloat()
    val horizonY = (h * 0.44f - pitchShift).coerceIn(h * 0.25f, h * 0.62f)
    val curveOffset = (curveDeg * 24.0).toFloat()
    val vanishX = (w * 0.5f + curveOffset).coerceIn(w * 0.22f, w * 0.78f)

    // 1. Hemisphere + Directional Sky & 400m Exponential-Squared Fog Band
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF0B1325), Color(0xFF1D2B44), Color(0xFF334766)),
            startY = 0f,
            endY = horizonY
        ),
        size = Size(w, horizonY)
    )

    // Distant Alpine Mountain Silhouette
    val mountainPath = Path().apply {
        moveTo(0f, horizonY)
        lineTo(w * 0.14f, horizonY - h * 0.11f)
        lineTo(w * 0.29f, horizonY - h * 0.04f)
        lineTo(w * 0.48f, horizonY - h * 0.14f)
        lineTo(w * 0.68f, horizonY - h * 0.05f)
        lineTo(w * 0.85f, horizonY - h * 0.12f)
        lineTo(w, horizonY - h * 0.03f)
        lineTo(w, horizonY)
        close()
    }
    drawPath(path = mountainPath, color = Color(0xFF1B263B))

    // 2. Ground Plane with Fog Blending
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF283645), Color(0xFF1A2521), Color(0xFF121A17)),
            startY = horizonY,
            endY = h
        ),
        topLeft = Offset(0f, horizonY),
        size = Size(w, h - horizonY)
    )

    // 3. Ballast Bed Trapezoid
    val ballastPath = Path().apply {
        moveTo(vanishX - 18f, horizonY)
        lineTo(vanishX + 18f, horizonY)
        lineTo(w * 0.82f, h)
        lineTo(w * 0.18f, h)
        close()
    }
    drawPath(path = ballastPath, color = Color(0xFF334155))

    // 4. Pooled Track Ties / Sleepers (Instanced-style loop, zero allocations)
    val tiePhase = ((positionMeters * 1.8) % 1.0).toFloat()
    val tieCount = 26
    for (i in 0 until tieCount) {
        val linearT = (i + tiePhase) / tieCount.toFloat()
        val p = linearT * linearT // Perspective depth curve
        val y = horizonY + p * (h - horizonY)
        val centerX = vanishX + (w * 0.5f - vanishX) * p
        val halfSpan = 12f + p * (w * 0.28f)
        drawLine(
            color = Color(0xFF1E293B),
            start = Offset(centerX - halfSpan, y),
            end = Offset(centerX + halfSpan, y),
            strokeWidth = (1.2f + p * 6f)
        )
    }

    // 5. Steel Rails (Left & Right Running Rails)
    drawLine(
        color = Color(0xFFCBD5E1),
        start = Offset(vanishX - 8f, horizonY),
        end = Offset(w * 0.28f, h),
        strokeWidth = 4.5f
    )
    drawLine(
        color = Color(0xFFCBD5E1),
        start = Offset(vanishX + 8f, horizonY),
        end = Offset(w * 0.72f, h),
        strokeWidth = 4.5f
    )

    // 6. Instanced Scenery & Catenary Poles with 3-tier LOD (80m & 200m thresholds)
    val sceneryPhase = ((positionMeters * 0.08) % 1.0).toFloat()
    for (i in 0 until 8) {
        val t = ((i / 8f) + sceneryPhase) % 1f
        val p = t * t
        val distMeters = (1f - t) * 350f
        val y = horizonY + p * (h - horizonY)
        val trackCenterX = vanishX + (w * 0.5f - vanishX) * p
        val spread = 32f + p * (w * 0.42f)

        // Left & right trees / structures with LOD switching at 80m and 200m
        val treeH = (12f + p * 110f)
        val treeW = treeH * 0.48f
        val leftX = trackCenterX - spread
        val rightX = trackCenterX + spread

        if (distMeters < 200f) {
            // LOD 0 (<80m) or LOD 1 (80..200m)
            drawRoundRect(
                color = if (distMeters < 80f) Color(0xFF15803D) else Color(0xFF166534),
                topLeft = Offset(leftX - treeW * 0.5f, y - treeH),
                size = Size(treeW, treeH * 0.85f),
                cornerRadius = CornerRadius(treeW * 0.35f, treeW * 0.35f)
            )
            // Catenary mast on right side
            drawLine(
                color = Color(0xFF64748B),
                start = Offset(rightX * 0.92f, y),
                end = Offset(rightX * 0.92f, y - treeH * 0.95f),
                strokeWidth = (1.5f + p * 3.5f)
            )
        } else {
            // LOD 2 (>200m): ultra-lightweight billboard rectangle
            drawRect(
                color = Color(0xFF1E3A2F),
                topLeft = Offset(leftX - treeW * 0.3f, y - treeH * 0.7f),
                size = Size(treeW * 0.6f, treeH * 0.7f)
            )
        }
    }

    // 7. Station Platform Visual when approaching station (< 300m)
    if (distanceToNextStation in 0.0..300.0) {
        val normDist = (1.0 - (distanceToNextStation / 300.0)).toFloat().coerceIn(0.1f, 1f)
        val platY = horizonY + normDist * normDist * (h - horizonY) * 0.75f
        val platW = 40f + normDist * 140f
        val platH = 14f + normDist * 38f
        drawRoundRect(
            color = Color(0xFFB45309),
            topLeft = Offset(vanishX - platW - 35f * normDist, platY - platH),
            size = Size(platW, platH),
            cornerRadius = CornerRadius(4f, 4f)
        )
    }

    // 8. Colourblind-Safe Trackside Signal Aspect (Square = Green, Diamond = Yellow, Circle = Red)
    val sigProgress = (1.0 - (distanceToNextSignal.coerceIn(0.0, 450.0) / 450.0)).toFloat()
    val sigY = horizonY + sigProgress * sigProgress * (h * 0.42f)
    val sigX = vanishX + 26f + sigProgress * (w * 0.22f)
    val sigScale = 0.65f + sigProgress * 0.95f

    // Signal mast & head housing
    drawLine(
        color = Color(0xFF94A3B8),
        start = Offset(sigX, sigY + 42f * sigScale),
        end = Offset(sigX, sigY - 18f * sigScale),
        strokeWidth = 3f * sigScale
    )
    drawRoundRect(
        color = Color(0xFF090D14),
        topLeft = Offset(sigX - 14f * sigScale, sigY - 24f * sigScale),
        size = Size(28f * sigScale, 36f * sigScale),
        cornerRadius = CornerRadius(6f, 6f)
    )
    val sigColor = Color(nextSignalAspect.colorHex)
    val r = 9f * sigScale
    val centerSig = Offset(sigX, sigY - 6f * sigScale)
    when (nextSignalAspect) {
        SignalAspect.CLEAR_GREEN -> {
            // SQUARE for CLEAR_GREEN
            drawRect(
                color = sigColor,
                topLeft = Offset(centerSig.x - r, centerSig.y - r),
                size = Size(r * 2f, r * 2f)
            )
        }
        SignalAspect.APPROACH_YELLOW -> {
            // DIAMOND for APPROACH_YELLOW
            val diamond = Path().apply {
                moveTo(centerSig.x, centerSig.y - r * 1.25f)
                lineTo(centerSig.x + r * 1.25f, centerSig.y)
                lineTo(centerSig.x, centerSig.y + r * 1.25f)
                lineTo(centerSig.x - r * 1.25f, centerSig.y)
                close()
            }
            drawPath(diamond, color = sigColor)
        }
        SignalAspect.STOP_RED -> {
            // CIRCLE for STOP_RED
            drawCircle(color = sigColor, radius = r * 1.1f, center = centerSig)
        }
    }

    // 9. Camera Mode Specific Foreground (Chase Cam 3D Locomotive + Consist vs. Cab View Cockpit)
    val locoSpec = HardcodedAssetLibrary.locomotives[locoIndex.coerceIn(0, HardcodedAssetLibrary.locomotives.lastIndex)]
    val bodyColor = customLiveryColor ?: locoSpec.primaryColor

    if (cameraMode == CameraViewMode.CHASE_CAM) {
        val locoCenterX = w * 0.5f
        val locoBottomY = h * 0.88f
        val locoW = (w * 0.22f).coerceIn(110f, 220f)
        val locoH = (h * 0.26f).coerceIn(90f, 180f)

        // Mandatory Blob Shadow (Flat dark ellipse under train — zero real-time shadow maps)
        drawOval(
            color = Color(0x99000000),
            topLeft = Offset(locoCenterX - locoW * 0.68f, locoBottomY - 18f),
            size = Size(locoW * 1.36f, 32f)
        )

        // Trailing Freight Cars preview in consist
        val carSpec = HardcodedAssetLibrary.freightCars[freightCarIndex.coerceIn(0, HardcodedAssetLibrary.freightCars.lastIndex)]
        val visibleCars = freightCarCount.coerceAtMost(3)
        for (c in visibleCars downTo 1) {
            val offsetX = curveOffset * 0.18f * c
            val carW = locoW * (1f - c * 0.12f)
            val carH = locoH * (0.72f - c * 0.08f)
            val carY = locoBottomY + c * 14f
            drawRoundRect(
                color = carSpec.bodyColor.copy(alpha = 0.88f),
                topLeft = Offset(locoCenterX - carW * 0.5f + offsetX, carY - carH),
                size = Size(carW, carH * 0.45f),
                cornerRadius = CornerRadius(6f, 6f)
            )
        }

        // Locomotive Main Body
        drawRoundRect(
            color = bodyColor,
            topLeft = Offset(locoCenterX - locoW * 0.5f, locoBottomY - locoH),
            size = Size(locoW, locoH),
            cornerRadius = CornerRadius(12f, 12f)
        )
        // Roof & Accent Stripe
        drawRect(
            color = locoSpec.accentColor,
            topLeft = Offset(locoCenterX - locoW * 0.5f, locoBottomY - locoH * 0.55f),
            size = Size(locoW, locoH * 0.16f)
        )
        // Rear Cab Windows
        drawRoundRect(
            color = Color(0xFF0F172A),
            topLeft = Offset(locoCenterX - locoW * 0.38f, locoBottomY - locoH * 0.88f),
            size = Size(locoW * 0.32f, locoH * 0.22f),
            cornerRadius = CornerRadius(4f, 4f)
        )
        drawRoundRect(
            color = Color(0xFF0F172A),
            topLeft = Offset(locoCenterX + locoW * 0.06f, locoBottomY - locoH * 0.88f),
            size = Size(locoW * 0.32f, locoH * 0.22f),
            cornerRadius = CornerRadius(4f, 4f)
        )

        // Pooled Exhaust / Steam Particles above Locomotive stack
        if (!reducedMotion) {
            for (i in particles.indices) {
                val pt = particles[i]
                if (pt.active) {
                    val alpha = (1f - (pt.life / pt.maxLife)).coerceIn(0f, 0.65f)
                    val px = locoCenterX + pt.x * 40f
                    val py = (locoBottomY - locoH) - (pt.y - 3.8f) * 28f
                    drawCircle(
                        color = Color(0xFFCBD5E1).copy(alpha = alpha),
                        radius = (pt.size * 12f).coerceIn(4f, 28f),
                        center = Offset(px, py)
                    )
                }
            }
        }
    } else {
        // CAB VIEW: Short Hood Nose + Windshield Pillars + Headlight Cone
        val shakeX = if (!reducedMotion && speedMps > 1.0) {
            (sin(positionMeters * 3.1) * 2.5).toFloat()
        } else 0f

        // Headlight glow cone on track ahead
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x44FDE047), Color.Transparent),
                center = Offset(vanishX, horizonY + (h - horizonY) * 0.45f),
                radius = w * 0.35f
            ),
            radius = w * 0.35f,
            center = Offset(vanishX, horizonY + (h - horizonY) * 0.45f)
        )

        // Short hood nose visible outside cab window
        val hoodPath = Path().apply {
            moveTo(w * 0.28f + shakeX, h)
            lineTo(w * 0.38f + shakeX, h * 0.76f)
            lineTo(w * 0.62f + shakeX, h * 0.76f)
            lineTo(w * 0.72f + shakeX, h)
            close()
        }
        drawPath(hoodPath, color = bodyColor)

        // Cab Frame Pillars & Center Post
        drawRect(
            color = Color(0xFF0F141C),
            topLeft = Offset(0f, 0f),
            size = Size(w, h * 0.06f)
        )
        drawRect(
            color = Color(0xFF141B26),
            topLeft = Offset(w * 0.49f + shakeX, 0f),
            size = Size(w * 0.02f, h * 0.82f)
        )
        drawRect(
            color = Color(0xFF0F141C),
            topLeft = Offset(0f, h * 0.82f),
            size = Size(w, h * 0.18f),
            style = Stroke(width = 6f)
        )
    }
}
