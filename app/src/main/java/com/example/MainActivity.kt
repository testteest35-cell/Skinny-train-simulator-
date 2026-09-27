package com.example

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.SaveSlotEntity
import com.example.ui.AiRailStudioScreen
import com.example.ui.BottomCabControlsDeck
import com.example.ui.EngineArchitectureDocsScreen
import com.example.ui.F3DiagnosticsOverlay
import com.example.ui.SimViewport3D
import com.example.ui.TopTelemetryHud
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CyanTelemetry
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.RailBorderSteel
import com.example.ui.theme.RailCardDark
import com.example.ui.theme.RailSurfaceDark
import com.example.ui.theme.SignalGreen

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                IronRailSimApp(viewModel = viewModel)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val state = viewModel.uiState.value
        return when (keyCode) {
            KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_DPAD_UP -> {
                viewModel.setThrottleNotch(state.throttleNotch + 1)
                true
            }
            KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_DPAD_DOWN -> {
                viewModel.setThrottleNotch(state.throttleNotch - 1)
                true
            }
            KeyEvent.KEYCODE_A -> {
                viewModel.setReverser(state.reverser - 1)
                true
            }
            KeyEvent.KEYCODE_D -> {
                viewModel.setReverser(state.reverser + 1)
                true
            }
            KeyEvent.KEYCODE_SPACE -> {
                viewModel.setAutoBrake((state.autoBrakePercent + 25f).coerceAtMost(100f))
                true
            }
            KeyEvent.KEYCODE_H -> {
                viewModel.soundHorn()
                true
            }
            KeyEvent.KEYCODE_B -> {
                viewModel.ringBell()
                true
            }
            KeyEvent.KEYCODE_V -> {
                viewModel.toggleSand()
                true
            }
            KeyEvent.KEYCODE_L -> {
                viewModel.cycleHeadlights()
                true
            }
            KeyEvent.KEYCODE_C -> {
                viewModel.cycleCameraMode()
                true
            }
            KeyEvent.KEYCODE_F3 -> {
                viewModel.toggleF3Diagnostics()
                true
            }
            KeyEvent.KEYCODE_F9 -> {
                viewModel.runF9SelfTest()
                true
            }
            KeyEvent.KEYCODE_M -> {
                viewModel.toggleMute()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }
}

@Composable
fun IronRailSimApp(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val saveSlots by viewModel.saveSlots.collectAsStateWithLifecycle()
    val surveyorItems by viewModel.surveyorItems.collectAsStateWithLifecycle()
    val mediaHistory by viewModel.generatedMediaHistory.collectAsStateWithLifecycle()

    BackHandler(enabled = state.activeTab != MainNavTab.SIMULATOR || state.isSurveyorMode) {
        if (state.activeTab != MainNavTab.SIMULATOR) {
            viewModel.selectTab(MainNavTab.SIMULATOR)
        } else if (state.isSurveyorMode) {
            viewModel.setSurveyorMode(false)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            NavigationBar(
                containerColor = RailSurfaceDark,
                modifier = Modifier.border(1.dp, RailBorderSteel)
            ) {
                NavigationBarItem(
                    selected = state.activeTab == MainNavTab.SIMULATOR,
                    onClick = { viewModel.selectTab(MainNavTab.SIMULATOR) },
                    icon = { Icon(Icons.Default.Train, contentDescription = "3D Simulator") },
                    label = { Text("3D Simulator") },
                    modifier = Modifier.testTag("nav_tab_simulator")
                )
                NavigationBarItem(
                    selected = state.activeTab == MainNavTab.AI_STUDIO,
                    onClick = { viewModel.selectTab(MainNavTab.AI_STUDIO) },
                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "AI Rail Studio") },
                    label = { Text("AI Rail Studio") },
                    modifier = Modifier.testTag("nav_tab_ai_studio")
                )
                NavigationBarItem(
                    selected = state.activeTab == MainNavTab.DOCS_QA,
                    onClick = { viewModel.selectTab(MainNavTab.DOCS_QA) },
                    icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "Engine & QA") },
                    label = { Text("Engine & QA") },
                    modifier = Modifier.testTag("nav_tab_docs_qa")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (state.activeTab) {
                MainNavTab.SIMULATOR -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            SimViewport3D(
                                positionMeters = state.positionMeters,
                                speedMps = state.speedMps,
                                gradientPercent = state.gradientPercent,
                                curveDeg = state.curveDeg,
                                throttleNotch = state.throttleNotch,
                                reverser = state.reverser,
                                autoBrakePercent = state.autoBrakePercent,
                                indBrakePercent = state.indBrakePercent,
                                brakePipePsi = state.brakePipePsi,
                                sandActive = state.sandActive,
                                headlightState = state.headlightState,
                                wipersActive = state.wipersActive,
                                wiperPhaseRad = state.wiperPhaseRad,
                                wheelRotationRad = state.wheelRotationRad,
                                timeOfDay = state.timeOfDay,
                                weather = state.weather,
                                qualityPreset = state.qualityPreset,
                                cameraMode = state.cameraMode,
                                locoIndex = state.selectedLocoIndex,
                                freightCarIndex = state.selectedFreightCarIndex,
                                freightCarCount = state.scenario.freightCarCount,
                                nextSignalAspect = state.nextSignalAspect,
                                distanceToNextSignal = state.distanceToNextSignal,
                                distanceToNextStation = state.distanceToNextStation,
                                particles = viewModel.physicsEngine.particlePool,
                                reducedMotion = state.reducedMotion,
                                customLiveryColor = state.customLiveryColor,
                                isSurveyorMode = state.isSurveyorMode,
                                surveyorItems = surveyorItems,
                                onSurveyorGridTap = { gx, gz -> viewModel.placeSurveyorItemAt(gx, gz) }
                            )

                            Column(modifier = Modifier.fillMaxWidth()) {
                                TopTelemetryHud(
                                    speedKmH = state.speedKmH,
                                    gradientPercent = state.gradientPercent,
                                    brakePipePsi = state.brakePipePsi,
                                    nextSignal = state.nextSignalAspect,
                                    distanceToNextSignal = state.distanceToNextSignal,
                                    distanceToNextStation = state.distanceToNextStation,
                                    positionMeters = state.positionMeters,
                                    scenario = state.scenario,
                                    scenarioScore = state.scenarioScore,
                                    wheelSlip = state.wheelSlip,
                                    spadPenalty = state.spadPenalty,
                                    cameraMode = state.cameraMode,
                                    timeOfDay = state.timeOfDay,
                                    weather = state.weather,
                                    qualityPreset = state.qualityPreset,
                                    statusMessage = state.statusMessage,
                                    subtitleCue = state.subtitleCue,
                                    uiScale = state.uiScale,
                                    f3Visible = state.f3Visible,
                                    onToggleF3 = viewModel::toggleF3Diagnostics,
                                    onRunF9SelfTest = viewModel::runF9SelfTest,
                                    onCycleCamera = viewModel::cycleCameraMode,
                                    onCycleTimeOfDay = viewModel::cycleTimeOfDay,
                                    onCycleWeather = viewModel::cycleWeather,
                                    onCycleQualityPreset = viewModel::cycleQualityPreset,
                                    onOpenSaveModal = { viewModel.setShowSaveModal(true) },
                                    onOpenAccessModal = { viewModel.setShowAccessModal(true) },
                                    muted = state.muted,
                                    onToggleMute = viewModel::toggleMute
                                )

                                if (state.f3Visible) {
                                    F3DiagnosticsOverlay(
                                        fps = state.fps,
                                        avgFrameMs = state.avgFrameMs,
                                        onePercentLowMs = state.onePercentLowMs,
                                        drawCalls = state.drawCalls,
                                        triangles = state.triangles,
                                        culledObjects = state.culledObjects,
                                        heapMb = state.heapMb,
                                        tickCount = state.tickCount,
                                        seed = state.seed,
                                        activeParticles = state.activeParticles,
                                        qualityTierLabel = state.qualityTierLabel,
                                        benchmarkRunning = state.benchmarkRunning,
                                        benchmarkReport = state.benchmarkReport,
                                        selfTestReport = state.selfTestReport,
                                        onStartBenchmark = viewModel::start30sBenchmark
                                    )
                                }
                            }
                        }

                        BottomCabControlsDeck(
                            isSurveyorMode = state.isSurveyorMode,
                            onToggleSurveyorMode = viewModel::setSurveyorMode,
                            throttleNotch = state.throttleNotch,
                            onThrottleChange = viewModel::setThrottleNotch,
                            reverser = state.reverser,
                            onReverserChange = viewModel::setReverser,
                            autoBrakePercent = state.autoBrakePercent,
                            onAutoBrakeChange = viewModel::setAutoBrake,
                            indBrakePercent = state.indBrakePercent,
                            onIndBrakeChange = viewModel::setIndBrake,
                            dynamicBrakeNotch = state.dynamicBrakeNotch,
                            onDynamicBrakeChange = viewModel::setDynamicBrake,
                            sandActive = state.sandActive,
                            onToggleSand = viewModel::toggleSand,
                            headlightState = state.headlightState,
                            onCycleHeadlights = viewModel::cycleHeadlights,
                            wipersActive = state.wipersActive,
                            onToggleWipers = viewModel::toggleWipers,
                            couplerSlackEnabled = state.couplerSlackEnabled,
                            onToggleCouplerSlack = viewModel::toggleCouplerSlack,
                            onSoundHorn = viewModel::soundHorn,
                            onRingBell = viewModel::ringBell,
                            onEmergencyBrake = viewModel::triggerEmergencyBrake,
                            selectedScenario = state.scenario,
                            onSelectScenario = viewModel::selectScenario,
                            selectedLocoIndex = state.selectedLocoIndex,
                            onSelectLoco = viewModel::selectLocomotive,
                            selectedSurveyorAssetId = state.selectedSurveyorAssetId,
                            isPlacingTrackSpline = state.isPlacingTrackSpline,
                            onSelectSurveyorModeType = viewModel::setSurveyorPlacementMode,
                            onSelectSurveyorAsset = viewModel::selectSurveyorAsset,
                            surveyorSnapAngleDeg = state.surveyorSnapAngleDeg,
                            onRotateSurveyorSnap = viewModel::rotateSurveyorSnap5Deg,
                            onSurveyorUndo = viewModel::surveyorUndo,
                            onSurveyorRedo = viewModel::surveyorRedo
                        )
                    }
                }

                MainNavTab.AI_STUDIO -> {
                    AiRailStudioScreen(
                        currentImageResult = state.aiImageResult,
                        currentVideoResult = state.aiVideoResult,
                        isBusy = state.aiBusy,
                        statusBanner = state.aiStatusBanner,
                        savedMediaHistory = mediaHistory,
                        onGenerateImage = viewModel::generateRailImage,
                        onEditImage = viewModel::editRailImage,
                        onAnimateVeoVideo = viewModel::animateVeoVideo,
                        onApplyLiveryFromBitmap = viewModel::applyLiveryFromBitmap
                    )
                }

                MainNavTab.DOCS_QA -> {
                    EngineArchitectureDocsScreen(
                        onLaunchSingleFileWebGlMode = viewModel::launchSingleFileWebGlMode,
                        onRunSelfTestNow = viewModel::runF9SelfTest
                    )
                }
            }
        }
    }

    if (state.showSaveModal) {
        SaveLoadSlotsDialog(
            saveSlots = saveSlots,
            onSaveSlot = { viewModel.saveToSlot(it, isAutoSave = false) },
            onLoadSlot = viewModel::loadFromSlot,
            onDismiss = { viewModel.setShowSaveModal(false) }
        )
    }

    if (state.showAccessModal) {
        AccessibilityDialog(
            uiScale = state.uiScale,
            onUiScaleChange = viewModel::setUiScale,
            reducedMotion = state.reducedMotion,
            onReducedMotionChange = viewModel::setReducedMotion,
            subtitlesEnabled = state.subtitlesEnabled,
            onSubtitlesChange = viewModel::setSubtitlesEnabled,
            onDismiss = { viewModel.setShowAccessModal(false) }
        )
    }
}

@Composable
private fun SaveLoadSlotsDialog(
    saveSlots: List<SaveSlotEntity>,
    onSaveSlot: (Int) -> Unit,
    onLoadSlot: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RailSurfaceDark,
        title = { Text("Deterministic Save Slots (Room DB)", color = AmberGold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Stores PRNG seed, tick count, consist position, and input log for bit-identical replay.",
                    style = MaterialTheme.typography.bodyMedium
                )
                for (idx in 1..3) {
                    val existing = saveSlots.firstOrNull { it.slotIndex == idx }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(RailCardDark)
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Slot $idx ${if (idx == 1) "(Autosave)" else ""}", style = MaterialTheme.typography.labelLarge, color = CyanTelemetry)
                            if (existing != null) {
                                Text(
                                    "${existing.scenarioId} • Pos ${existing.positionMeters.toInt()}m • Tick ${existing.tickCount}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            } else {
                                Text("Empty Slot", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = { onSaveSlot(idx) },
                                colors = ButtonDefaults.buttonColors(containerColor = AmberGold, contentColor = Color.Black),
                                modifier = Modifier.testTag("btn_save_slot_$idx")
                            ) {
                                Text("Save", style = MaterialTheme.typography.labelSmall)
                            }
                            Button(
                                onClick = { onLoadSlot(idx) },
                                enabled = existing != null,
                                colors = ButtonDefaults.buttonColors(containerColor = SignalGreen, contentColor = Color.Black),
                                modifier = Modifier.testTag("btn_load_slot_$idx")
                            ) {
                                Text("Load", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = AmberGold) }
        }
    )
}

@Composable
private fun AccessibilityDialog(
    uiScale: Float,
    onUiScaleChange: (Float) -> Unit,
    reducedMotion: Boolean,
    onReducedMotionChange: (Boolean) -> Unit,
    subtitlesEnabled: Boolean,
    onSubtitlesChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RailSurfaceDark,
        title = { Text("Accessibility & Ergonomics", color = AmberGold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("HUD & Text Scale: ${(uiScale * 100).toInt()}% (75% - 150%)", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = uiScale,
                    onValueChange = onUiScaleChange,
                    valueRange = 0.75f..1.50f,
                    modifier = Modifier.testTag("slider_ui_scale")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Subtitles for Horn / Bell / Alarms", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = subtitlesEnabled, onCheckedChange = onSubtitlesChange)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Reduced Motion (Disable Shake & Smoke)", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = reducedMotion, onCheckedChange = onReducedMotionChange)
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Colourblind Signal Shapes Active: Square = Clear Green, Diamond = Caution Yellow, Circle = Stop Red.",
                    style = MaterialTheme.typography.labelSmall,
                    color = SignalGreen
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done", color = AmberGold) }
        }
    )
}
