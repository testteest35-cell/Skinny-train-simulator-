package com.example

import android.os.Bundle
import android.view.KeyEvent
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sim.SignalAspect
import com.example.ui.AiRailStudioScreen
import com.example.ui.SimViewport3D
import com.example.ui.theme.AmberGold
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.RailBorderSteel
import com.example.ui.theme.RailSurfaceDark
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private var activeWebView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                IronRailSimApp(
                    viewModel = viewModel,
                    onWebViewReady = { activeWebView = it }
                )
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
                val nextBrake = if (state.autoBrakePercent >= 75f) 0f else state.autoBrakePercent + 25f
                viewModel.setAutoBrake(nextBrake)
                true
            }
            KeyEvent.KEYCODE_C -> {
                viewModel.cycleCameraMode()
                true
            }
            KeyEvent.KEYCODE_H -> {
                viewModel.soundHorn()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IronRailSimApp(
    viewModel: MainViewModel,
    onWebViewReady: (WebView) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val surveyorItems by viewModel.surveyorItems.collectAsStateWithLifecycle()
    val mediaHistory by viewModel.generatedMediaHistory.collectAsStateWithLifecycle()

    BackHandler(enabled = state.activeTab != MainNavTab.SIMULATOR) {
        viewModel.selectTab(MainNavTab.SIMULATOR)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Full-Screen Native 3D Perspective Train Simulator Viewport
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
            onSurveyorGridTap = { gx, gz -> viewModel.placeSurveyorItemAt(gx, gz) },
            onWebViewReady = onWebViewReady,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Minimal, Sleek, Transparent Corner HUD Overlay (Corners Only, Clean Sans-Serif)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // TOP CORNERS ROW
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Top-Left Corner: Route Signal, Grade & Active 3D Camera Badge
                CornerHudCard(modifier = Modifier.testTag("hud_top_left_card")) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val sigColor = when (state.nextSignalAspect) {
                            SignalAspect.CLEAR_GREEN -> Color(0xFF22C55E)
                            SignalAspect.APPROACH_YELLOW -> Color(0xFFFACC15)
                            SignalAspect.STOP_RED -> Color(0xFFEF4444)
                        }
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(sigColor)
                        )
                        Text(
                            text = state.nextSignalAspect.label,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.SansSerif
                        )
                        CornerDivider()
                        val gradeStr = String.format(Locale.US, "%+.1f%%", state.gradientPercent)
                        Text(
                            text = "Grade $gradeStr",
                            color = Color(0xFFE2E8F0),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.SansSerif
                        )
                        CornerDivider()
                        Text(
                            text = "Cam: ${state.cameraMode.label}",
                            color = Color(0xFF38BDF8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }

                // Top-Right Corner: Camera Cycle, Horn, Time of Day & Livery Studio Toggles
                CornerHudCard(modifier = Modifier.testTag("hud_top_right_card")) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CornerHudButton(
                            label = "Camera (C)",
                            accent = true,
                            testTag = "btn_cycle_camera",
                            onClick = { viewModel.cycleCameraMode() }
                        )
                        CornerHudButton(
                            label = "Horn (H)",
                            accent = false,
                            testTag = "btn_sound_horn",
                            onClick = { viewModel.soundHorn() }
                        )
                        CornerHudButton(
                            label = state.timeOfDay.label,
                            accent = false,
                            testTag = "btn_time_of_day",
                            onClick = { viewModel.cycleTimeOfDay() }
                        )
                        CornerHudButton(
                            label = "AI Livery",
                            accent = false,
                            testTag = "btn_open_ai_studio",
                            onClick = { viewModel.selectTab(MainNavTab.AI_STUDIO) }
                        )
                    }
                }
            }

            // BOTTOM CORNERS ROW
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                // Bottom-Left Corner: Sleek Speedometer, Throttle, Reverser & Brake Readout
                CornerHudCard(modifier = Modifier.testTag("hud_bottom_left_card")) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        // Speed Readout (km/h)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = String.format(Locale.US, "%.1f", state.speedKmH),
                                color = Color.White,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif,
                                modifier = Modifier.testTag("hud_speed_value")
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "km/h",
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.SansSerif,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }

                        // Throttle Indicator
                        CornerMetricItem(
                            label = "THROTTLE",
                            value = "N${state.throttleNotch}",
                            valueColor = Color(0xFFFBBF24)
                        )

                        // Reverser Indicator
                        val revText = when (state.reverser) {
                            1 -> "FWD"
                            -1 -> "REV"
                            else -> "NEU"
                        }
                        CornerMetricItem(
                            label = "REVERSER",
                            value = revText,
                            valueColor = Color(0xFF38BDF8)
                        )

                        // Brake Indicator
                        val brkPct = state.autoBrakePercent.toInt()
                        val psi = state.brakePipePsi.toInt()
                        CornerMetricItem(
                            label = "BRAKE",
                            value = "$brkPct% ($psi PSI)",
                            valueColor = if (brkPct > 0) Color(0xFFF87171) else Color.White
                        )
                    }
                }

                // Bottom-Right Corner: Sleek Interactive Controls (W/S, A/D, Space)
                CornerHudCard(modifier = Modifier.testTag("hud_bottom_right_card")) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CornerHudButton(
                            label = "Throttle - (S)",
                            accent = false,
                            testTag = "btn_throttle_down",
                            onClick = { viewModel.setThrottleNotch(state.throttleNotch - 1) }
                        )
                        CornerHudButton(
                            label = "Throttle + (W)",
                            accent = true,
                            testTag = "btn_throttle_up",
                            onClick = { viewModel.setThrottleNotch(state.throttleNotch + 1) }
                        )
                        CornerHudButton(
                            label = "Rev (A/D)",
                            accent = false,
                            testTag = "btn_reverser_toggle",
                            onClick = {
                                val nextRev = if (state.reverser == 1) -1 else 1
                                viewModel.setReverser(nextRev)
                            }
                        )
                        CornerHudButton(
                            label = if (state.autoBrakePercent > 0f) "Release Brake" else "Brake (Space)",
                            accent = false,
                            testTag = "btn_brake_toggle",
                            onClick = {
                                val nextBrake = if (state.autoBrakePercent >= 75f) 0f else state.autoBrakePercent + 25f
                                viewModel.setAutoBrake(nextBrake)
                            }
                        )
                    }
                }
            }
        }

        // 3. Full-screen modal only if user explicitly opens AI Rail Livery Studio
        if (state.activeTab == MainNavTab.AI_STUDIO) {
            Surface(
                color = RailSurfaceDark.copy(alpha = 0.96f),
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "AI Rail Livery & Veo 3.1 Studio",
                            color = AmberGold,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1E293B))
                                .border(1.dp, RailBorderSteel, RoundedCornerShape(8.dp))
                                .clickable { viewModel.selectTab(MainNavTab.SIMULATOR) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("btn_back_to_3d_sim")
                        ) {
                            Text(
                                text = "Back to 3D Simulator",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    }

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
            }
        }
    }
}

@Composable
private fun CornerHudCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0F172A).copy(alpha = 0.46f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        content()
    }
}

@Composable
private fun CornerHudButton(
    label: String,
    accent: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    val bgColor = if (accent) Color(0xFFF59E0B).copy(alpha = 0.88f) else Color(0xFF1E293B).copy(alpha = 0.62f)
    val textColor = if (accent) Color(0xFF0F172A) else Color.White
    val borderColor = if (accent) Color(0xFFFBBF24) else Color.White.copy(alpha = 0.18f)

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.SansSerif
        )
    }
}

@Composable
private fun CornerMetricItem(
    label: String,
    value: String,
    valueColor: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
            text = label,
            color = Color(0xFFCBD5E1).copy(alpha = 0.78f),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.SansSerif
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif
        )
    }
}

@Composable
private fun CornerDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(12.dp)
            .background(Color.White.copy(alpha = 0.22f))
    )
}
