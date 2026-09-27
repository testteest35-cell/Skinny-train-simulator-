package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SettingsAccessibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sim.BenchmarkReport
import com.example.sim.HardcodedAssetLibrary
import com.example.sim.HeadlightState
import com.example.sim.QualityPreset
import com.example.sim.ScenarioId
import com.example.sim.SelfTestReport
import com.example.sim.SignalAspect
import com.example.sim.TimeOfDayMode
import com.example.sim.WeatherMode
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CyanTelemetry
import com.example.ui.theme.RailBorderSteel
import com.example.ui.theme.RailCardDark
import com.example.ui.theme.RailSurfaceDark
import com.example.ui.theme.SignalGreen
import com.example.ui.theme.SignalRed
import kotlin.math.abs

@Composable
fun TopTelemetryHud(
    speedKmH: Double,
    gradientPercent: Double,
    brakePipePsi: Float,
    nextSignal: SignalAspect,
    distanceToNextSignal: Double,
    distanceToNextStation: Double,
    positionMeters: Double,
    scenario: ScenarioId,
    scenarioScore: Int,
    wheelSlip: Boolean,
    spadPenalty: Boolean,
    cameraMode: CameraViewMode,
    timeOfDay: TimeOfDayMode,
    weather: WeatherMode,
    qualityPreset: QualityPreset,
    statusMessage: String,
    subtitleCue: String?,
    uiScale: Float,
    f3Visible: Boolean,
    onToggleF3: () -> Unit,
    onRunF9SelfTest: () -> Unit,
    onCycleCamera: () -> Unit,
    onCycleTimeOfDay: () -> Unit,
    onCycleWeather: () -> Unit,
    onCycleQualityPreset: () -> Unit,
    onOpenSaveModal: () -> Unit,
    onOpenAccessModal: () -> Unit,
    muted: Boolean,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Surface(
            color = RailSurfaceDark.copy(alpha = 0.92f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, RailBorderSteel, RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(
                                text = "${"%.1f".format(abs(speedKmH))} KM/H",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontSize = (20f * uiScale).sp,
                                    color = if (abs(speedKmH) > scenario.maxSpeedLimitKmH || spadPenalty) SignalRed else AmberGold
                                ),
                                modifier = Modifier.testTag("hud_speed_text")
                            )
                            Text(
                                text = "LIM ${scenario.maxSpeedLimitKmH.toInt()} | GRD ${"%+.1f".format(gradientPercent)}%",
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = (11f * uiScale).sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        SignalAspectBadge(
                            aspect = nextSignal,
                            distanceMeters = distanceToNextSignal,
                            uiScale = uiScale
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "STOP: ${distanceToNextStation.toInt()} m",
                            style = MaterialTheme.typography.labelLarge.copy(fontSize = (12f * uiScale).sp),
                            color = CyanTelemetry
                        )
                        Text(
                            text = "BP: ${brakePipePsi.toInt()} PSI | ${cameraMode.label}",
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = (10f * uiScale).sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Environment & Quality Quick Bar (Time of Day, Weather, Quality Preset)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(RailCardDark)
                            .border(1.dp, RailBorderSteel, RoundedCornerShape(6.dp))
                            .clickable { onCycleTimeOfDay() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("btn_cycle_tod")
                    ) {
                        Text("Time: ${timeOfDay.label}", style = MaterialTheme.typography.labelSmall, color = AmberGold)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(RailCardDark)
                            .border(1.dp, RailBorderSteel, RoundedCornerShape(6.dp))
                            .clickable { onCycleWeather() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("btn_cycle_weather")
                    ) {
                        Text("Wx: ${weather.label}", style = MaterialTheme.typography.labelSmall, color = CyanTelemetry)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(RailCardDark)
                            .border(1.dp, RailBorderSteel, RoundedCornerShape(6.dp))
                            .clickable { onCycleQualityPreset() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("btn_cycle_quality")
                    ) {
                        Text("Quality: ${qualityPreset.label}", style = MaterialTheme.typography.labelSmall, color = SignalGreen)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "Score: $scenarioScore%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Mini Route Strip Map + Action Toolbar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MiniRouteStripMap(
                        positionMeters = positionMeters,
                        scenario = scenario,
                        nextSignal = nextSignal,
                        modifier = Modifier
                            .weight(1f)
                            .height(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        IconButton(
                            onClick = onCycleCamera,
                            modifier = Modifier.size(34.dp).testTag("btn_cycle_camera")
                        ) {
                            Icon(Icons.Default.Cameraswitch, contentDescription = "Cycle 5 Cameras (C)", tint = AmberGold)
                        }
                        IconButton(
                            onClick = onToggleF3,
                            modifier = Modifier.size(34.dp).testTag("btn_toggle_f3")
                        ) {
                            Icon(
                                Icons.Default.Assessment,
                                contentDescription = "Toggle F3 Diagnostics",
                                tint = if (f3Visible) SignalGreen else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(
                            onClick = onRunF9SelfTest,
                            modifier = Modifier.size(34.dp).testTag("btn_run_f9_selftest")
                        ) {
                            Icon(Icons.Default.BugReport, contentDescription = "Run F9 Self-Test", tint = CyanTelemetry)
                        }
                        IconButton(
                            onClick = onOpenSaveModal,
                            modifier = Modifier.size(34.dp).testTag("btn_open_saves")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = "Save / Load Slots", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        IconButton(
                            onClick = onOpenAccessModal,
                            modifier = Modifier.size(34.dp).testTag("btn_open_accessibility")
                        ) {
                            Icon(Icons.Default.SettingsAccessibility, contentDescription = "Accessibility Settings", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        IconButton(
                            onClick = onToggleMute,
                            modifier = Modifier.size(34.dp).testTag("btn_toggle_mute")
                        ) {
                            Icon(
                                if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Toggle Mute (M)",
                                tint = if (muted) SignalRed else SignalGreen
                            )
                        }
                    }
                }

                // Status & SPAD / Wheel Slip Ticker
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = (10f * uiScale).sp),
                        color = if (wheelSlip || spadPenalty) SignalRed else AmberGold,
                        maxLines = 1
                    )
                    if (wheelSlip || spadPenalty) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = "Alert", tint = SignalRed, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (spadPenalty) "SPAD PENALTY!" else "WHEEL SLIP!",
                                style = MaterialTheme.typography.labelSmall,
                                color = SignalRed
                            )
                        }
                    }
                }
            }
        }

        if (!subtitleCue.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                color = Color(0xDD090D14),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .border(1.dp, AmberGold, RoundedCornerShape(8.dp))
            ) {
                Text(
                    text = subtitleCue,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = (12f * uiScale).sp),
                    color = AmberGold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun SignalAspectBadge(
    aspect: SignalAspect,
    distanceMeters: Double,
    uiScale: Float
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(RailCardDark)
            .border(1.dp, Color(aspect.colorHex), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Canvas(modifier = Modifier.size(18.dp)) {
            val c = Color(aspect.colorHex)
            val r = size.minDimension * 0.42f
            val centerPt = Offset(size.width * 0.5f, size.height * 0.5f)
            when (aspect) {
                SignalAspect.CLEAR_GREEN -> {
                    drawRect(
                        color = c,
                        topLeft = Offset(centerPt.x - r, centerPt.y - r),
                        size = Size(r * 2f, r * 2f)
                    )
                }
                SignalAspect.APPROACH_YELLOW -> {
                    val path = Path().apply {
                        moveTo(centerPt.x, centerPt.y - r * 1.2f)
                        lineTo(centerPt.x + r * 1.2f, centerPt.y)
                        lineTo(centerPt.x, centerPt.y + r * 1.2f)
                        lineTo(centerPt.x - r * 1.2f, centerPt.y)
                        close()
                    }
                    drawPath(path, color = c)
                }
                SignalAspect.STOP_RED -> {
                    drawCircle(color = c, radius = r, center = centerPt)
                }
            }
        }
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(
                text = "${aspect.label} [${aspect.shapeName}]",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = (10f * uiScale).sp,
                    fontWeight = FontWeight.Bold
                ),
                color = Color(aspect.colorHex)
            )
            Text(
                text = "${distanceMeters.toInt()}m ahead",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = (9f * uiScale).sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun MiniRouteStripMap(
    positionMeters: Double,
    scenario: ScenarioId,
    nextSignal: SignalAspect,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF090D14))
            .border(1.dp, RailBorderSteel, RoundedCornerShape(6.dp))
    ) {
        val w = size.width
        val h = size.height
        val maxRouteMeters = 4800f
        val cy = h * 0.5f

        drawLine(
            color = Color(0xFF334155),
            start = Offset(10f, cy),
            end = Offset(w - 10f, cy),
            strokeWidth = 3f
        )

        val stations = scenario.stations
        for (i in stations.indices) {
            val st = stations[i]
            val sx = 10f + ((st.distanceMeters.toFloat() / maxRouteMeters).coerceIn(0f, 1f)) * (w - 20f)
            drawCircle(
                color = CyanTelemetry,
                radius = 4.5f,
                center = Offset(sx, cy)
            )
        }

        val trainX = 10f + (((positionMeters.toFloat() % maxRouteMeters) / maxRouteMeters).coerceIn(0f, 1f)) * (w - 20f)
        drawRoundRect(
            color = AmberGold,
            topLeft = Offset(trainX - 6f, cy - 5f),
            size = Size(12f, 10f)
        )
        val sigX = (trainX + 24f).coerceAtMost(w - 12f)
        drawCircle(
            color = Color(nextSignal.colorHex),
            radius = 3.5f,
            center = Offset(sigX, cy)
        )
    }
}

@Composable
fun F3DiagnosticsOverlay(
    fps: Float,
    avgFrameMs: Float,
    onePercentLowMs: Float,
    drawCalls: Int,
    triangles: Int,
    culledObjects: Int,
    heapMb: Float,
    tickCount: Long,
    seed: Int,
    activeParticles: Int,
    qualityTierLabel: String,
    benchmarkRunning: Boolean,
    benchmarkReport: BenchmarkReport?,
    selfTestReport: SelfTestReport?,
    onStartBenchmark: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xEB080C12),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .padding(horizontal = 10.dp)
            .border(1.dp, AmberGold, RoundedCornerShape(10.dp))
            .testTag("f3_diagnostics_overlay")
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "F3 TRAINZ: A NEW ERA DIAGNOSTICS",
                    style = MaterialTheme.typography.labelLarge,
                    color = AmberGold
                )
                Button(
                    onClick = onStartBenchmark,
                    enabled = !benchmarkRunning,
                    colors = ButtonDefaults.buttonColors(containerColor = AmberGold, contentColor = Color.Black),
                    modifier = Modifier.height(30.dp).testTag("btn_record_30s_benchmark")
                ) {
                    Text(
                        text = if (benchmarkRunning) "Benchmarking..." else "Record 30s Benchmark",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "FPS: ${"%.1f".format(fps)} (60Hz Target) | Frame: ${"%.2f".format(avgFrameMs)} ms | 1% Low: ${"%.2f".format(onePercentLowMs)} ms",
                style = MaterialTheme.typography.labelMedium,
                color = SignalGreen
            )
            Text(
                text = "Draw Calls: $drawCalls / 80 | 3D Mesh Triangles: $triangles | Frustum Culled: $culledObjects objs",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Memory: ${"%.1f".format(heapMb)} MB | Active Pooled Particles: $activeParticles / 64",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Tick: $tickCount (60Hz) | PRNG Seed: $seed | $qualityTierLabel",
                style = MaterialTheme.typography.labelSmall,
                color = CyanTelemetry
            )

            if (benchmarkReport != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "BENCHMARK VERDICT: ${if (benchmarkReport.passedAllCriteria) "PASS" else "FAIL"} — ${benchmarkReport.verdictSummary}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (benchmarkReport.passedAllCriteria) SignalGreen else SignalRed
                )
            }

            if (selfTestReport != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "F9 SELF-TEST: ${if (selfTestReport.overallPass) "PASS" else "FAIL"} (Peak ${"%.1f".format(selfTestReport.peakSpeedReachedKmH)} km/h -> 0.0 km/h stop, ${selfTestReport.assetsVerifiedCount} 3D assets OK)",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selfTestReport.overallPass) SignalGreen else SignalRed
                )
            }
        }
    }
}

@Composable
fun BottomCabControlsDeck(
    isSurveyorMode: Boolean,
    onToggleSurveyorMode: (Boolean) -> Unit,
    throttleNotch: Int,
    onThrottleChange: (Int) -> Unit,
    reverser: Int,
    onReverserChange: (Int) -> Unit,
    autoBrakePercent: Float,
    onAutoBrakeChange: (Float) -> Unit,
    indBrakePercent: Float,
    onIndBrakeChange: (Float) -> Unit,
    dynamicBrakeNotch: Int,
    onDynamicBrakeChange: (Int) -> Unit,
    sandActive: Boolean,
    onToggleSand: () -> Unit,
    headlightState: HeadlightState,
    onCycleHeadlights: () -> Unit,
    wipersActive: Boolean,
    onToggleWipers: () -> Unit,
    couplerSlackEnabled: Boolean,
    onToggleCouplerSlack: (Boolean) -> Unit,
    onSoundHorn: () -> Unit,
    onRingBell: () -> Unit,
    onEmergencyBrake: () -> Unit,
    selectedScenario: ScenarioId,
    onSelectScenario: (ScenarioId) -> Unit,
    selectedLocoIndex: Int,
    onSelectLoco: (Int) -> Unit,
    selectedSurveyorAssetId: String,
    isPlacingTrackSpline: Boolean,
    onSelectSurveyorModeType: (Boolean) -> Unit,
    onSelectSurveyorAsset: (String) -> Unit,
    surveyorSnapAngleDeg: Int,
    onRotateSurveyorSnap: () -> Unit,
    onSurveyorUndo: () -> Unit,
    onSurveyorRedo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = RailSurfaceDark.copy(alpha = 0.95f),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, RailBorderSteel, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Mode Switcher + 4 Sessions (Free Drive, Passenger, Freight, Tutorial)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = !isSurveyorMode,
                        onClick = { onToggleSurveyorMode(false) },
                        label = { Text("Drive", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag("chip_driver_mode")
                    )
                    FilterChip(
                        selected = isSurveyorMode,
                        onClick = { onToggleSurveyorMode(true) },
                        label = { Text("Surveyor", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag("chip_surveyor_mode")
                    )
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    itemsIndexed(ScenarioId.entries) { _, scen ->
                        val active = scen == selectedScenario
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (active) AmberGold else RailCardDark)
                                .clickable { onSelectScenario(scen) }
                                .padding(horizontal = 7.dp, vertical = 5.dp)
                                .testTag("scenario_${scen.name.lowercase()}")
                        ) {
                            Text(
                                text = scen.title,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (active) Color.Black else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (isSurveyorMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = isPlacingTrackSpline,
                            onClick = { onSelectSurveyorModeType(true) },
                            label = { Text("Track Spline") }
                        )
                        FilterChip(
                            selected = !isPlacingTrackSpline,
                            onClick = { onSelectSurveyorModeType(false) },
                            label = { Text("Scenery (20)") }
                        )
                        Button(
                            onClick = onRotateSurveyorSnap,
                            colors = ButtonDefaults.buttonColors(containerColor = RailCardDark)
                        ) {
                            Text("Snap: ${surveyorSnapAngleDeg}°", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Row {
                        IconButton(onClick = onSurveyorUndo, modifier = Modifier.testTag("btn_surveyor_undo")) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", tint = AmberGold)
                        }
                        IconButton(onClick = onSurveyorRedo, modifier = Modifier.testTag("btn_surveyor_redo")) {
                            Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", tint = CyanTelemetry)
                        }
                    }
                }

                if (!isPlacingTrackSpline) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        itemsIndexed(HardcodedAssetLibrary.sceneryItems) { _, item ->
                            val selected = item.id == selectedSurveyorAssetId
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) AmberGold else RailCardDark)
                                    .border(1.dp, RailBorderSteel, RoundedCornerShape(8.dp))
                                    .clickable { onSelectSurveyorAsset(item.id) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "${item.name} (${item.triangleCountLod0}t)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) Color.Black else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Tap the Surveyor grid to lay 3D track splines with 5° snap & 20-step Undo/Redo.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Row 1: Notched Throttle (0..8) & Reverser (REV / NEU / FWD)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "THROTTLE NOTCH: N$throttleNotch | DYN BRAKE: B$dynamicBrakeNotch",
                            style = MaterialTheme.typography.labelSmall,
                            color = AmberGold
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.padding(top = 3.dp)
                        ) {
                            for (n in 0..8) {
                                val active = throttleNotch == n
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(width = 25.dp, height = 26.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (active) AmberGold else RailCardDark)
                                        .clickable { onThrottleChange(n) }
                                        .testTag("throttle_notch_$n")
                                ) {
                                    Text(
                                        text = "$n",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (active) Color.Black else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Column(horizontalAlignment = Alignment.End) {
                        Text("REVERSER (F/N/R)", style = MaterialTheme.typography.labelSmall, color = CyanTelemetry)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 3.dp)
                        ) {
                            listOf(-1 to "REV", 0 to "NEU", 1 to "FWD").forEach { (dir, lbl) ->
                                val active = reverser == dir
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .height(26.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (active) CyanTelemetry else RailCardDark)
                                        .clickable { onReverserChange(dir) }
                                        .padding(horizontal = 7.dp)
                                        .testTag("reverser_$lbl")
                                ) {
                                    Text(
                                        text = lbl,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (active) Color.Black else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(5.dp))

                // Row 2: Train Brake & Independent Brake Sliders + Horn / Bell / E-Stop
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "TRAIN BRAKE: ${autoBrakePercent.toInt()}% | IND BRAKE: ${indBrakePercent.toInt()}%",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Slider(
                            value = autoBrakePercent,
                            onValueChange = {
                                onAutoBrakeChange(it)
                                onIndBrakeChange(it * 0.75f)
                            },
                            valueRange = 0f..100f,
                            modifier = Modifier.height(24.dp).testTag("slider_auto_brake")
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(
                            onClick = onSoundHorn,
                            colors = ButtonDefaults.buttonColors(containerColor = AmberGold, contentColor = Color.Black),
                            modifier = Modifier.height(32.dp).testTag("btn_horn")
                        ) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("HORN", style = MaterialTheme.typography.labelSmall)
                        }
                        Button(
                            onClick = onRingBell,
                            colors = ButtonDefaults.buttonColors(containerColor = RailCardDark),
                            modifier = Modifier.height(32.dp).testTag("btn_bell")
                        ) {
                            Text("BELL", style = MaterialTheme.typography.labelSmall)
                        }
                        Button(
                            onClick = onEmergencyBrake,
                            colors = ButtonDefaults.buttonColors(containerColor = SignalRed, contentColor = Color.White),
                            modifier = Modifier.height(32.dp).testTag("btn_emergency_brake")
                        ) {
                            Text("STOP", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                // Row 3: Trainz Cab Auxiliary Switches (Sand, Headlights, Wipers) + Locomotive Roster
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (sandActive) AmberGold else RailCardDark)
                            .border(1.dp, RailBorderSteel, RoundedCornerShape(6.dp))
                            .clickable { onToggleSand() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("btn_toggle_sand")
                    ) {
                        Text(
                            text = "SAND: ${if (sandActive) "ON" else "OFF"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (sandActive) Color.Black else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (headlightState != HeadlightState.OFF) CyanTelemetry else RailCardDark)
                            .border(1.dp, RailBorderSteel, RoundedCornerShape(6.dp))
                            .clickable { onCycleHeadlights() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("btn_cycle_headlights")
                    ) {
                        Text(
                            text = "LIGHTS: ${headlightState.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (headlightState != HeadlightState.OFF) Color.Black else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (wipersActive) SignalGreen else RailCardDark)
                            .border(1.dp, RailBorderSteel, RoundedCornerShape(6.dp))
                            .clickable { onToggleWipers() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("btn_toggle_wipers")
                    ) {
                        Text(
                            text = "WIPERS: ${if (wipersActive) "ON" else "OFF"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (wipersActive) Color.Black else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        itemsIndexed(HardcodedAssetLibrary.locomotives) { idx, loco ->
                            val selected = idx == selectedLocoIndex
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (selected) loco.primaryColor else RailCardDark)
                                    .clickable { onSelectLoco(idx) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${loco.name} (${loco.triangleCount}t)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) Color.Black else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
