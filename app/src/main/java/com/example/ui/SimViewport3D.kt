package com.example.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.sim.HeadlightState
import com.example.sim.PooledParticle
import com.example.sim.QualityPreset
import com.example.sim.SignalAspect
import com.example.sim.TimeOfDayMode
import com.example.sim.WeatherMode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class CameraViewMode(val label: String) {
    CAB_VIEW("Cab Interior 3D"),
    CHASE_CAM("Chase Cam 3D"),
    FREE_ORBIT("Free-Roam Orbit"),
    TRACKSIDE_CAM("Trackside Signal Cam"),
    STATION_FLYBY("Cinematic Fly-By"),
    WEBGL_SINGLE_FILE("Three.js WebGL2 Engine")
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SimViewport3D(
    positionMeters: Double,
    speedMps: Double,
    gradientPercent: Double,
    curveDeg: Double,
    throttleNotch: Int,
    reverser: Int,
    autoBrakePercent: Float,
    indBrakePercent: Float,
    brakePipePsi: Float,
    sandActive: Boolean,
    headlightState: HeadlightState,
    wipersActive: Boolean,
    wiperPhaseRad: Float,
    wheelRotationRad: Float,
    timeOfDay: TimeOfDayMode,
    weather: WeatherMode,
    qualityPreset: QualityPreset,
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

    var orbitYawRad by remember { mutableFloatStateOf(0.42f) }
    var orbitPitchRad by remember { mutableFloatStateOf(0.24f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F17))
            .pointerInput(isSurveyorMode, cameraMode) {
                if (isSurveyorMode) {
                    detectTapGestures { offset ->
                        val normX = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0.05f, 0.95f)
                        val normZ = (offset.y / size.height.coerceAtLeast(1)).coerceIn(0.05f, 0.95f)
                        onSurveyorGridTap(normX * 100f, normZ * 100f)
                    }
                } else {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        orbitYawRad = (orbitYawRad + dragAmount.x * 0.008f) % (2f * PI.toFloat())
                        orbitPitchRad = (orbitPitchRad + dragAmount.y * 0.005f).coerceIn(0.06f, 0.65f)
                    }
                }
            }
            .testTag("sim_viewport_canvas")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (isSurveyorMode) {
                drawSurveyorGrid(surveyorItems)
            } else {
                drawTrainzNewEra3DWorld(
                    positionMeters = positionMeters,
                    speedMps = speedMps,
                    gradientPercent = gradientPercent,
                    curveDeg = curveDeg,
                    throttleNotch = throttleNotch,
                    reverser = reverser,
                    autoBrakePercent = autoBrakePercent,
                    indBrakePercent = indBrakePercent,
                    brakePipePsi = brakePipePsi,
                    sandActive = sandActive,
                    headlightState = headlightState,
                    wipersActive = wipersActive,
                    wiperPhaseRad = wiperPhaseRad,
                    wheelRotationRad = wheelRotationRad,
                    timeOfDay = timeOfDay,
                    weather = weather,
                    qualityPreset = qualityPreset,
                    cameraMode = cameraMode,
                    orbitYawRad = orbitYawRad,
                    orbitPitchRad = orbitPitchRad,
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
    drawRect(color = Color(0xFF0A121E), size = size)

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

    val trackNodes = surveyorItems.filter { it.isTrackSplineNode }
    if (trackNodes.size >= 2) {
        for (i in 0 until trackNodes.size - 1) {
            val a = trackNodes[i]
            val b = trackNodes[i + 1]
            val p1 = Offset((a.gridX / 100f) * w, (a.gridZ / 100f) * h)
            val p2 = Offset((b.gridX / 100f) * w, (b.gridZ / 100f) * h)
            drawLine(color = Color(0xFF64748B), start = p1, end = p2, strokeWidth = 14f, cap = StrokeCap.Round)
            drawLine(color = Color(0xFFF59E0B), start = p1, end = p2, strokeWidth = 4f, cap = StrokeCap.Round)
        }
    } else {
        drawLine(
            color = Color(0xFF475569),
            start = Offset(w * 0.15f, h * 0.85f),
            end = Offset(w * 0.85f, h * 0.15f),
            strokeWidth = 10f
        )
    }

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
            drawRect(color = col, topLeft = Offset(px - 11f, py - 11f), size = Size(22f, 22f))
            val rad = Math.toRadians(item.rotationDeg.toDouble())
            drawLine(
                color = Color.White,
                start = Offset(px, py),
                end = Offset(px + cos(rad).toFloat() * 18f, py + sin(rad).toFloat() * 18f),
                strokeWidth = 2.5f
            )
        }
    }
}

private fun DrawScope.drawTrainzNewEra3DWorld(
    positionMeters: Double,
    speedMps: Double,
    gradientPercent: Double,
    curveDeg: Double,
    throttleNotch: Int,
    reverser: Int,
    autoBrakePercent: Float,
    indBrakePercent: Float,
    brakePipePsi: Float,
    sandActive: Boolean,
    headlightState: HeadlightState,
    wipersActive: Boolean,
    wiperPhaseRad: Float,
    wheelRotationRad: Float,
    timeOfDay: TimeOfDayMode,
    weather: WeatherMode,
    qualityPreset: QualityPreset,
    cameraMode: CameraViewMode,
    orbitYawRad: Float,
    orbitPitchRad: Float,
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

    val camYawOffset = when (cameraMode) {
        CameraViewMode.FREE_ORBIT -> sin(orbitYawRad) * 110f
        CameraViewMode.TRACKSIDE_CAM -> -85f
        CameraViewMode.STATION_FLYBY -> sin((positionMeters * 0.03).toFloat()) * 130f
        else -> 0f
    }
    val camPitchOffset = when (cameraMode) {
        CameraViewMode.FREE_ORBIT -> (orbitPitchRad - 0.22f) * 180f
        CameraViewMode.STATION_FLYBY -> 45f
        else -> 0f
    }

    val pitchShift = (gradientPercent * 8.0).toFloat() - camPitchOffset
    val horizonY = (h * 0.43f - pitchShift).coerceIn(h * 0.22f, h * 0.64f)
    val curveOffset = (curveDeg * 24.0).toFloat() + camYawOffset
    val vanishX = (w * 0.5f + curveOffset).coerceIn(w * 0.16f, w * 0.84f)

    // 1. Skybox Gradient + Sun/Moon + Volumetric Cloud Layer (Time of Day + Weather aware)
    val skyTopColor = if (weather == WeatherMode.OVERCAST || weather == WeatherMode.LIGHT_RAIN) {
        Color(0xFF1E293B)
    } else if (weather == WeatherMode.FOG) {
        Color(0xFF475569)
    } else {
        Color(timeOfDay.skyTopHex)
    }
    val skyHorizonColor = if (weather == WeatherMode.FOG) {
        Color(0xFF94A3B8)
    } else if (weather == WeatherMode.OVERCAST || weather == WeatherMode.LIGHT_RAIN) {
        Color(0xFF475569)
    } else {
        Color(timeOfDay.skyHorizonHex)
    }

    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(skyTopColor, skyHorizonColor),
            startY = 0f,
            endY = horizonY
        ),
        size = Size(w, horizonY)
    )

    // Sun / Moon disc with atmospheric bloom
    if (weather != WeatherMode.FOG) {
        val sunCenter = Offset(w * 0.74f, horizonY * 0.45f)
        val sunColor = if (timeOfDay == TimeOfDayMode.NIGHT) Color(0xFFE2E8F0) else Color(0xFFFDE047)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(sunColor, sunColor.copy(alpha = 0.25f), Color.Transparent),
                center = sunCenter,
                radius = 72f
            ),
            radius = 72f,
            center = sunCenter
        )
    }

    // 2. Rolling 3D Hills, Mountains & Valley Silhouette
    val hillColor = if (timeOfDay == TimeOfDayMode.NIGHT) Color(0xFF09101D) else Color(0xFF1E293B)
    val mountainPath = Path().apply {
        moveTo(0f, horizonY)
        val steps = 12
        for (s in 0..steps) {
            val sx = (w / steps) * s
            val mh = (sin(s * 0.8 + positionMeters * 0.0006) * 28.0 + cos(s * 1.5) * 16.0 + 28.0).toFloat()
            lineTo(sx, horizonY - mh)
        }
        lineTo(w, horizonY)
        close()
    }
    drawPath(path = mountainPath, color = hillColor)

    // 3. Ground Plane & Valley Water Riverband
    val groundTop = if (weather == WeatherMode.FOG) Color(0xFF64748B) else Color(timeOfDay.groundHex)
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(groundTop, Color(0xFF0E1714), Color(0xFF080D0C)),
            startY = horizonY,
            endY = h
        ),
        topLeft = Offset(0f, horizonY),
        size = Size(w, h - horizonY)
    )

    // Distant River Reflection Strip on left valley
    val riverPath = Path().apply {
        moveTo(vanishX - 45f, horizonY)
        lineTo(vanishX - 28f, horizonY)
        lineTo(w * 0.08f, h)
        lineTo(0f, h)
        close()
    }
    drawPath(
        path = riverPath,
        color = if (timeOfDay == TimeOfDayMode.NIGHT) Color(0xFF0F2942) else Color(0xFF0284C7).copy(alpha = 0.45f)
    )

    // 4. 3D Extruded Ballast Bed & Instanced Wooden Sleepers
    val ballastPath = Path().apply {
        moveTo(vanishX - 19f, horizonY)
        lineTo(vanishX + 19f, horizonY)
        lineTo(w * 0.84f, h)
        lineTo(w * 0.16f, h)
        close()
    }
    drawPath(path = ballastPath, color = Color(0xFF334155))

    val tiePhase = ((positionMeters * 1.9) % 1.0).toFloat()
    val tieCount = 30
    for (i in 0 until tieCount) {
        val linearT = (i + tiePhase) / tieCount.toFloat()
        val p = linearT * linearT
        val y = horizonY + p * (h - horizonY)
        val centerX = vanishX + (w * 0.5f - vanishX) * p
        val halfSpan = 13f + p * (w * 0.29f)
        drawLine(
            color = Color(0xFF1E293B),
            start = Offset(centerX - halfSpan, y),
            end = Offset(centerX + halfSpan, y),
            strokeWidth = (1.3f + p * 6.5f)
        )
    }

    // 5. Extruded 3D Steel Rails (Wet specular reflection when Light Rain is active)
    val railColor = if (weather == WeatherMode.LIGHT_RAIN) Color(0xFFE0F2FE) else Color(0xFFCBD5E1)
    drawLine(
        color = railColor,
        start = Offset(vanishX - 8f, horizonY),
        end = Offset(w * 0.28f, h),
        strokeWidth = 5f
    )
    drawLine(
        color = railColor,
        start = Offset(vanishX + 8f, horizonY),
        end = Offset(w * 0.72f, h),
        strokeWidth = 5f
    )

    // 6. Overhead Catenary Portals, Contact Wires, Forests & Town Buildings
    // Contact Wire
    drawLine(
        color = Color(0xFF94A3B8).copy(alpha = 0.7f),
        start = Offset(vanishX, horizonY - 16f),
        end = Offset(w * 0.5f, 0f),
        strokeWidth = 1.8f
    )

    val sceneryPhase = ((positionMeters * 0.075) % 1.0).toFloat()
    for (i in 0 until 9) {
        val t = ((i / 9f) + sceneryPhase) % 1f
        val p = t * t
        val distMeters = (1f - t) * 380f
        if (distMeters > 400f * qualityPreset.drawDistanceScale) continue

        val y = horizonY + p * (h - horizonY)
        val trackCenterX = vanishX + (w * 0.5f - vanishX) * p
        val spread = 30f + p * (w * 0.38f)
        val mastH = 16f + p * 135f

        // Overhead Catenary Portal (Left Mast + Right Mast + Cross-Beam)
        val leftMastX = trackCenterX - spread * 0.82f
        val rightMastX = trackCenterX + spread * 0.82f
        drawLine(
            color = Color(0xFF64748B),
            start = Offset(leftMastX, y),
            end = Offset(leftMastX, y - mastH),
            strokeWidth = 1.5f + p * 3.5f
        )
        drawLine(
            color = Color(0xFF64748B),
            start = Offset(rightMastX, y),
            end = Offset(rightMastX, y - mastH),
            strokeWidth = 1.5f + p * 3.5f
        )
        drawLine(
            color = Color(0xFF475569),
            start = Offset(leftMastX, y - mastH * 0.92f),
            end = Offset(rightMastX, y - mastH * 0.92f),
            strokeWidth = 1.2f + p * 2.5f
        )

        // Multi-tiered 3D Pine Trees on Left
        val treeX = trackCenterX - spread * 1.35f
        val treeW = mastH * 0.55f
        val foliageColor = if (timeOfDay == TimeOfDayMode.NIGHT) Color(0xFF062E1A) else Color(0xFF15803D)
        val canopyPath = Path().apply {
            moveTo(treeX, y - mastH * 1.15f)
            lineTo(treeX + treeW * 0.5f, y - mastH * 0.2f)
            lineTo(treeX - treeW * 0.5f, y - mastH * 0.2f)
            close()
        }
        drawPath(canopyPath, color = foliageColor)

        // Town Building on Right with Night Lit Windows
        if (i % 2 == 0) {
            val bldgX = trackCenterX + spread * 1.15f
            val bldgW = mastH * 0.62f
            val bldgH = mastH * 0.68f
            drawRoundRect(
                color = Color(0xFF475569),
                topLeft = Offset(bldgX, y - bldgH),
                size = Size(bldgW, bldgH),
                cornerRadius = CornerRadius(3f, 3f)
            )
            if (timeOfDay.nightGlowIntensity > 0f) {
                drawRect(
                    color = Color(0xFFFDE047).copy(alpha = timeOfDay.nightGlowIntensity),
                    topLeft = Offset(bldgX + bldgW * 0.22f, y - bldgH * 0.72f),
                    size = Size(bldgW * 0.22f, bldgH * 0.22f)
                )
            }
        }
    }

    // 7. 3D Station Platform, Canopy, Footbridge & Glowing Lamps when within 340m
    if (distanceToNextStation in 0.0..340.0) {
        val normDist = (1.0 - (distanceToNextStation / 340.0)).toFloat().coerceIn(0.12f, 1f)
        val p = normDist * normDist
        val platY = horizonY + p * (h - horizonY) * 0.78f
        val platW = 48f + p * 165f
        val platH = 18f + p * 46f
        val platLeft = vanishX - platW - 28f * normDist

        // Concrete platform edge + brick depot building
        drawRoundRect(
            color = Color(0xFF64748B),
            topLeft = Offset(platLeft, platY - platH * 0.35f),
            size = Size(platW, platH * 0.35f),
            cornerRadius = CornerRadius(3f, 3f)
        )
        drawRoundRect(
            color = Color(0xFFB45309),
            topLeft = Offset(platLeft + platW * 0.1f, platY - platH * 1.25f),
            size = Size(platW * 0.75f, platH * 0.9f),
            cornerRadius = CornerRadius(4f, 4f)
        )
        // Overhead Steel Footbridge across tracks
        drawRect(
            color = Color(0xFF334155),
            topLeft = Offset(platLeft + platW * 0.6f, platY - platH * 1.75f),
            size = Size(platW * 1.6f, platH * 0.28f)
        )
        // Platform Lamp Glow at Dusk/Night
        if (timeOfDay.nightGlowIntensity > 0f) {
            drawCircle(
                color = Color(0x66FDE047),
                radius = 24f * normDist,
                center = Offset(platLeft + platW * 0.8f, platY - platH * 1.1f)
            )
        }
    }

    // 8. 3D Block Signal (Both Colour-Light + Moving Semaphore Arm)
    val sigProgress = (1.0 - (distanceToNextSignal.coerceIn(0.0, 450.0) / 450.0)).toFloat()
    val sigY = horizonY + sigProgress * sigProgress * (h * 0.44f)
    val sigX = vanishX + 26f + sigProgress * (w * 0.24f)
    val sigScale = 0.68f + sigProgress * 1.05f
    val sigColor = Color(nextSignalAspect.colorHex)

    // Mast
    drawLine(
        color = Color(0xFF94A3B8),
        start = Offset(sigX, sigY + 46f * sigScale),
        end = Offset(sigX, sigY - 26f * sigScale),
        strokeWidth = 3.5f * sigScale
    )
    // Semaphore Arm (rotates by aspect: 0 deg for Red Stop, -22 deg for Caution, -45 deg for Clear)
    val semRad = Math.toRadians(nextSignalAspect.semaphoreAngleDeg.toDouble())
    drawLine(
        color = sigColor,
        start = Offset(sigX, sigY - 20f * sigScale),
        end = Offset(
            sigX + cos(semRad).toFloat() * 24f * sigScale,
            sigY - 20f * sigScale + sin(semRad).toFloat() * 24f * sigScale
        ),
        strokeWidth = 4.5f * sigScale,
        cap = StrokeCap.Round
    )
    // Signal Head Housing + Aspect Shape (Square/Diamond/Circle) + Bloom Halo
    drawRoundRect(
        color = Color(0xFF090D14),
        topLeft = Offset(sigX - 14f * sigScale, sigY - 24f * sigScale),
        size = Size(28f * sigScale, 36f * sigScale),
        cornerRadius = CornerRadius(6f, 6f)
    )
    val centerSig = Offset(sigX, sigY - 6f * sigScale)
    if (qualityPreset.bloomEnabled) {
        drawCircle(
            color = sigColor.copy(alpha = 0.32f),
            radius = 18f * sigScale,
            center = centerSig
        )
    }
    val r = 9f * sigScale
    when (nextSignalAspect) {
        SignalAspect.CLEAR_GREEN -> {
            drawRect(color = sigColor, topLeft = Offset(centerSig.x - r, centerSig.y - r), size = Size(r * 2f, r * 2f))
        }
        SignalAspect.APPROACH_YELLOW -> {
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
            drawCircle(color = sigColor, radius = r * 1.1f, center = centerSig)
        }
    }

    // 9. Camera-Specific 3D Train or 3D Cab Interior
    val locoSpec = HardcodedAssetLibrary.locomotives[locoIndex.coerceIn(0, HardcodedAssetLibrary.locomotives.lastIndex)]
    val bodyColor = customLiveryColor ?: locoSpec.primaryColor

    if (cameraMode == CameraViewMode.CAB_VIEW) {
        draw3DCabInteriorView(
            w = w,
            h = h,
            horizonY = horizonY,
            vanishX = vanishX,
            bodyColor = bodyColor,
            speedMps = speedMps,
            throttleNotch = throttleNotch,
            reverser = reverser,
            autoBrakePercent = autoBrakePercent,
            indBrakePercent = indBrakePercent,
            brakePipePsi = brakePipePsi,
            sandActive = sandActive,
            headlightState = headlightState,
            wipersActive = wipersActive,
            wiperPhaseRad = wiperPhaseRad,
            positionMeters = positionMeters,
            reducedMotion = reducedMotion
        )
    } else {
        drawDetailed3DLocomotiveAndConsist(
            w = w,
            h = h,
            locoSpec = locoSpec,
            bodyColor = bodyColor,
            freightCarIndex = freightCarIndex,
            freightCarCount = freightCarCount,
            curveOffset = curveOffset,
            orbitYawRad = if (cameraMode == CameraViewMode.FREE_ORBIT) orbitYawRad else 0.32f,
            wheelRotationRad = wheelRotationRad,
            headlightState = headlightState,
            shadowsEnabled = qualityPreset.shadowsEnabled,
            nightGlowIntensity = timeOfDay.nightGlowIntensity,
            particles = particles,
            reducedMotion = reducedMotion
        )
    }

    // 10. Weather Overlay (Light Rain Streaks or Heavy Fog Veil)
    if (weather == WeatherMode.LIGHT_RAIN && !reducedMotion) {
        val tickShift = ((positionMeters * 40.0) % h.toDouble()).toFloat()
        for (i in 0 until 55) {
            val rx = ((i * 73f + tickShift * 0.4f) % w)
            val ry = ((i * 41f + tickShift) % h)
            drawLine(
                color = Color(0x88BAE6FD),
                start = Offset(rx, ry),
                end = Offset(rx - 6f, ry + 20f),
                strokeWidth = 1.5f
            )
        }
    } else if (weather == WeatherMode.FOG) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0x6694A3B8), Color(0x2294A3B8)),
                startY = horizonY * 0.5f,
                endY = h
            ),
            size = size
        )
    }
}

private fun DrawScope.drawDetailed3DLocomotiveAndConsist(
    w: Float,
    h: Float,
    locoSpec: com.example.sim.LocomotiveSpec,
    bodyColor: Color,
    freightCarIndex: Int,
    freightCarCount: Int,
    curveOffset: Float,
    orbitYawRad: Float,
    wheelRotationRad: Float,
    headlightState: HeadlightState,
    shadowsEnabled: Boolean,
    nightGlowIntensity: Float,
    particles: Array<PooledParticle>,
    reducedMotion: Boolean
) {
    val locoCenterX = w * 0.5f
    val locoBottomY = h * 0.89f
    val locoW = (w * 0.23f).coerceIn(120f, 230f)
    val locoH = (h * 0.28f).coerceIn(100f, 190f)
    val sideDepthX = (sin(orbitYawRad) * 52f).coerceIn(-65f, 65f)

    // 1. Real-Time Soft Shadow Projection beneath Consist
    if (shadowsEnabled) {
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xAA000000), Color.Transparent),
                center = Offset(locoCenterX + sideDepthX * 0.3f, locoBottomY - 8f),
                radius = locoW * 0.95f
            ),
            topLeft = Offset(locoCenterX - locoW * 0.78f, locoBottomY - 22f),
            size = Size(locoW * 1.56f, 38f)
        )
    }

    // 2. Trailing 3D Rolling Stock Consist (Coaches with lit windows / Wagons)
    val carSpec = HardcodedAssetLibrary.freightCars[freightCarIndex.coerceIn(0, HardcodedAssetLibrary.freightCars.lastIndex)]
    val visibleCars = freightCarCount.coerceAtMost(4)
    for (c in visibleCars downTo 1) {
        val offsetX = curveOffset * 0.16f * c + sideDepthX * 0.35f * c
        val carW = locoW * (1f - c * 0.11f)
        val carH = locoH * (0.74f - c * 0.08f)
        val carY = locoBottomY - locoH * 0.32f - c * 18f
        val carTopLeft = Offset(locoCenterX - carW * 0.5f + offsetX, carY - carH * 0.45f)
        drawRoundRect(
            color = carSpec.bodyColor.copy(alpha = 0.92f),
            topLeft = carTopLeft,
            size = Size(carW, carH * 0.45f),
            cornerRadius = CornerRadius(6f, 6f)
        )
        // Passenger Coach Windows (glowing warm at Dusk/Night)
        for (win in 0 until 4) {
            val wx = carTopLeft.x + carW * (0.12f + win * 0.21f)
            val wy = carTopLeft.y + carH * 0.12f
            drawRect(
                color = if (nightGlowIntensity > 0f) Color(0xFFFDE047) else Color(0xFF0F172A),
                topLeft = Offset(wx, wy),
                size = Size(carW * 0.14f, carH * 0.14f)
            )
        }
    }

    // 3. 3D Bogie Trucks, Rotating Spoked Wheels & Connecting Rods
    val bogieTop = locoBottomY - 28f
    drawRoundRect(
        color = Color(0xFF0F172A),
        topLeft = Offset(locoCenterX - locoW * 0.46f, bogieTop),
        size = Size(locoW * 0.92f, 22f),
        cornerRadius = CornerRadius(4f, 4f)
    )
    for (wIdx in -1..1) {
        val wx = locoCenterX + wIdx * (locoW * 0.28f)
        val wy = locoBottomY - 12f
        drawCircle(color = Color(0xFF334155), radius = 13f, center = Offset(wx, wy))
        drawCircle(color = Color(0xFF94A3B8), radius = 13f, center = Offset(wx, wy), style = Stroke(width = 2.2f))
        // Rotating Wheel Spoke & Side Rod Pin
        val pinX = wx + cos(wheelRotationRad) * 9f
        val pinY = wy + sin(wheelRotationRad) * 9f
        drawLine(color = Color(0xFFE2E8F0), start = Offset(wx, wy), end = Offset(pinX, pinY), strokeWidth = 2.5f)
    }

    // 4. 3D Isometric Extruded Side Body Panel (Metallic PBR Shading)
    val mainTop = locoBottomY - locoH
    val mainBottom = locoBottomY - 24f
    if (sideDepthX != 0f) {
        val sideXEdge = if (sideDepthX >= 0f) locoCenterX + locoW * 0.5f else locoCenterX - locoW * 0.5f
        val sidePath = Path().apply {
            moveTo(sideXEdge, mainBottom)
            lineTo(sideXEdge + sideDepthX, mainBottom - 22f)
            lineTo(sideXEdge + sideDepthX, mainTop - 14f)
            lineTo(sideXEdge, mainTop)
            close()
        }
        drawPath(path = sidePath, color = bodyColor.copy(alpha = 0.78f))
    }

    // 5. Locomotive Main Painted Metal Body with Metallic Specular Highlight
    drawRoundRect(
        brush = Brush.horizontalGradient(
            colors = listOf(bodyColor.copy(alpha = 0.9f), bodyColor, Color(0xFFF8FAFC).copy(alpha = 0.85f), bodyColor),
            startX = locoCenterX - locoW * 0.5f,
            endX = locoCenterX + locoW * 0.5f
        ),
        topLeft = Offset(locoCenterX - locoW * 0.5f, mainTop),
        size = Size(locoW, mainBottom - mainTop),
        cornerRadius = CornerRadius(10f, 10f)
    )

    // Livery Trim Band, Radiator Grille & Number Plate
    drawRect(
        color = locoSpec.accentColor,
        topLeft = Offset(locoCenterX - locoW * 0.5f, mainTop + (mainBottom - mainTop) * 0.44f),
        size = Size(locoW, (mainBottom - mainTop) * 0.18f)
    )
    // Number plate box
    drawRoundRect(
        color = Color(0xFF090D14),
        topLeft = Offset(locoCenterX - 24f, mainTop + 8f),
        size = Size(48f, 14f),
        cornerRadius = CornerRadius(3f, 3f)
    )

    // Cab Windows with Subtle Glass Reflection
    val winTop = mainTop + (mainBottom - mainTop) * 0.14f
    val winH = (mainBottom - mainTop) * 0.24f
    drawRoundRect(
        color = Color(0xFF0F172A),
        topLeft = Offset(locoCenterX - locoW * 0.40f, winTop),
        size = Size(locoW * 0.34f, winH),
        cornerRadius = CornerRadius(4f, 4f)
    )
    drawRoundRect(
        color = Color(0xFF0F172A),
        topLeft = Offset(locoCenterX + locoW * 0.06f, winTop),
        size = Size(locoW * 0.34f, winH),
        cornerRadius = CornerRadius(4f, 4f)
    )

    // Rooftop Cooling Fans & 3-Chime Horn Cluster
    drawRoundRect(
        color = Color(0xFF475569),
        topLeft = Offset(locoCenterX - 18f, mainTop - 7f),
        size = Size(36f, 7f),
        cornerRadius = CornerRadius(3f, 3f)
    )
    drawCircle(color = Color(0xFFF59E0B), radius = 4.5f, center = Offset(locoCenterX, mainTop - 9f))

    // Heavy Janney Knuckle Coupler & Yellow Safety Handrails
    drawRoundRect(
        color = Color(0xFF1E293B),
        topLeft = Offset(locoCenterX - 16f, mainBottom - 6f),
        size = Size(32f, 16f),
        cornerRadius = CornerRadius(4f, 4f)
    )
    drawRect(
        color = Color(0xFFFACC15),
        topLeft = Offset(locoCenterX - locoW * 0.45f, mainBottom - 36f),
        size = Size(locoW * 0.90f, 28f),
        style = Stroke(width = 2.5f)
    )

    // 6. Twin Headlights & Volumetric Light Cone
    if (headlightState != HeadlightState.OFF) {
        val beamAlpha = if (headlightState == HeadlightState.BRIGHT) 0.55f else 0.24f
        val lightY = mainTop + (mainBottom - mainTop) * 0.52f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFDE047).copy(alpha = beamAlpha), Color.Transparent),
                center = Offset(locoCenterX, lightY),
                radius = 75f
            ),
            radius = 75f,
            center = Offset(locoCenterX, lightY)
        )
        drawCircle(color = Color(0xFFFEF08A), radius = 6.5f, center = Offset(locoCenterX - 8f, lightY))
        drawCircle(color = Color(0xFFFEF08A), radius = 6.5f, center = Offset(locoCenterX + 8f, lightY))
    }

    // 7. Pooled Exhaust / Steam Plumes
    if (!reducedMotion) {
        for (i in particles.indices) {
            val pt = particles[i]
            if (pt.active) {
                val alpha = (1f - (pt.life / pt.maxLife)).coerceIn(0f, 0.65f)
                val px = locoCenterX + pt.x * 42f
                val py = mainTop - (pt.y - 3.8f) * 28f
                drawCircle(
                    color = Color(0xFFCBD5E1).copy(alpha = alpha),
                    radius = (pt.size * 12f).coerceIn(4f, 28f),
                    center = Offset(px, py)
                )
            }
        }
    }
}

/**
 * First-Person 3D Cab Interior View with animated 3D Throttle Lever,
 * Train Brake Handle, Independent Brake, Reverser, Analog Dials, and Windshield Wipers.
 */
private fun DrawScope.draw3DCabInteriorView(
    w: Float,
    h: Float,
    horizonY: Float,
    vanishX: Float,
    bodyColor: Color,
    speedMps: Double,
    throttleNotch: Int,
    reverser: Int,
    autoBrakePercent: Float,
    indBrakePercent: Float,
    brakePipePsi: Float,
    sandActive: Boolean,
    headlightState: HeadlightState,
    wipersActive: Boolean,
    wiperPhaseRad: Float,
    positionMeters: Double,
    reducedMotion: Boolean
) {
    val shakeX = if (!reducedMotion && kotlin.math.abs(speedMps) > 1.0) {
        (sin(positionMeters * 3.1) * 2.2).toFloat()
    } else 0f

    // 1. Headlight Beam Illuminating Track Ahead
    if (headlightState != HeadlightState.OFF) {
        val alpha = if (headlightState == HeadlightState.BRIGHT) 0.36f else 0.16f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFDE047).copy(alpha = alpha), Color.Transparent),
                center = Offset(vanishX, horizonY + (h - horizonY) * 0.44f),
                radius = w * 0.38f
            ),
            radius = w * 0.38f,
            center = Offset(vanishX, horizonY + (h - horizonY) * 0.44f)
        )
    }

    // 2. Exterior Short Hood Nose visible through Windshield
    val deskTopY = h * 0.66f
    val hoodPath = Path().apply {
        moveTo(w * 0.28f + shakeX, deskTopY)
        lineTo(w * 0.38f + shakeX, deskTopY - h * 0.12f)
        lineTo(w * 0.62f + shakeX, deskTopY - h * 0.12f)
        lineTo(w * 0.72f + shakeX, deskTopY)
        close()
    }
    drawPath(hoodPath, color = bodyColor)

    // 3. Animated Windshield Wipers (Left & Right Blades)
    val wiperSweepAngle = -PI.toFloat() * 0.5f + (if (wipersActive) sin(wiperPhaseRad) * 0.55f else -0.35f)
    val wiperLength = (h * 0.22f).coerceIn(70f, 140f)
    for (pivotX in listOf(w * 0.26f + shakeX, w * 0.74f + shakeX)) {
        val pivotY = deskTopY - 6f
        val tipX = pivotX + cos(wiperSweepAngle) * wiperLength
        val tipY = pivotY + sin(wiperSweepAngle) * wiperLength
        drawLine(
            color = Color(0xFF0F172A),
            start = Offset(pivotX, pivotY),
            end = Offset(tipX, tipY),
            strokeWidth = 4.5f,
            cap = StrokeCap.Round
        )
    }

    // 4. Cab Windshield Pillars & Center Divider Post
    drawRect(color = Color(0xFF0B1018), topLeft = Offset(0f, 0f), size = Size(w, h * 0.05f))
    drawRect(color = Color(0xFF0F141C), topLeft = Offset(w * 0.49f + shakeX, 0f), size = Size(w * 0.02f, deskTopY))

    // 5. 3D Cab Control Desk Console
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF1E293B), Color(0xFF0F172A), Color(0xFF090D14)),
            startY = deskTopY,
            endY = h
        ),
        topLeft = Offset(0f, deskTopY),
        size = Size(w, h - deskTopY)
    )
    drawLine(
        color = Color(0xFF475569),
        start = Offset(0f, deskTopY),
        end = Offset(w, deskTopY),
        strokeWidth = 3f
    )

    // 6. Analog Speedometer & Brake Pipe Pressure Gauges with Moving Needles
    val speedDialCenter = Offset(w * 0.44f, deskTopY + (h - deskTopY) * 0.46f)
    val brakeDialCenter = Offset(w * 0.56f, deskTopY + (h - deskTopY) * 0.46f)
    val dialRadius = ((h - deskTopY) * 0.34f).coerceIn(24f, 44f)

    // Speedometer Dial
    drawCircle(color = Color(0xFF090D14), radius = dialRadius, center = speedDialCenter)
    drawCircle(color = Color(0xFFF59E0B), radius = dialRadius, center = speedDialCenter, style = Stroke(width = 3f))
    val speedRatio = ((kotlin.math.abs(speedMps) * 3.6) / 140.0).toFloat().coerceIn(0f, 1f)
    val speedAngle = PI.toFloat() * 0.75f + speedRatio * PI.toFloat() * 1.5f
    drawLine(
        color = Color(0xFFEF4444),
        start = speedDialCenter,
        end = Offset(
            speedDialCenter.x + cos(speedAngle) * (dialRadius * 0.8f),
            speedDialCenter.y + sin(speedAngle) * (dialRadius * 0.8f)
        ),
        strokeWidth = 3f,
        cap = StrokeCap.Round
    )

    // Brake Pipe Pressure Dial
    drawCircle(color = Color(0xFF090D14), radius = dialRadius, center = brakeDialCenter)
    drawCircle(color = Color(0xFF38BDF8), radius = dialRadius, center = brakeDialCenter, style = Stroke(width = 3f))
    val bpRatio = ((brakePipePsi - 50f) / 45f).coerceIn(0f, 1f)
    val bpAngle = PI.toFloat() * 0.75f + bpRatio * PI.toFloat() * 1.5f
    drawLine(
        color = Color(0xFF10B981),
        start = brakeDialCenter,
        end = Offset(
            brakeDialCenter.x + cos(bpAngle) * (dialRadius * 0.8f),
            brakeDialCenter.y + sin(bpAngle) * (dialRadius * 0.8f)
        ),
        strokeWidth = 3f,
        cap = StrokeCap.Round
    )

    // 7. Animated 3D Levers (Reverser, Throttle 0-8, Train Brake, Independent Brake)
    val leverBaseY = deskTopY + (h - deskTopY) * 0.52f
    val slotWidth = w * 0.16f

    // Left Console: Reverser + Throttle Lever (visibly moves with notch 0..8)
    val thrSlotLeft = w * 0.14f
    drawRoundRect(
        color = Color(0xFF090D14),
        topLeft = Offset(thrSlotLeft, leverBaseY - 8f),
        size = Size(slotWidth, 16f),
        cornerRadius = CornerRadius(4f, 4f)
    )
    val thrHandleX = thrSlotLeft + (throttleNotch / 8f) * (slotWidth - 18f)
    drawRoundRect(
        color = Color(0xFFF59E0B),
        topLeft = Offset(thrHandleX, leverBaseY - 28f),
        size = Size(18f, 48f),
        cornerRadius = CornerRadius(5f, 5f)
    )

    // Reverser Handle (FWD / NEU / REV)
    val revCenterY = leverBaseY - (reverser * 16f)
    drawRoundRect(
        color = Color(0xFF38BDF8),
        topLeft = Offset(w * 0.06f, revCenterY - 10f),
        size = Size(28f, 20f),
        cornerRadius = CornerRadius(4f, 4f)
    )

    // Right Console: Train Brake & Independent Brake Handles
    val brkSlotLeft = w * 0.68f
    drawRoundRect(
        color = Color(0xFF090D14),
        topLeft = Offset(brkSlotLeft, leverBaseY - 8f),
        size = Size(slotWidth, 16f),
        cornerRadius = CornerRadius(4f, 4f)
    )
    val autoBrkX = brkSlotLeft + (autoBrakePercent / 100f) * (slotWidth - 18f)
    drawRoundRect(
        color = Color(0xFFEF4444),
        topLeft = Offset(autoBrkX, leverBaseY - 28f),
        size = Size(18f, 48f),
        cornerRadius = CornerRadius(5f, 5f)
    )

    val indBrkX = brkSlotLeft + (indBrakePercent / 100f) * (slotWidth - 14f)
    drawRoundRect(
        color = Color(0xFFFACC15),
        topLeft = Offset(indBrkX, leverBaseY + 18f),
        size = Size(14f, 26f),
        cornerRadius = CornerRadius(4f, 4f)
    )
}
