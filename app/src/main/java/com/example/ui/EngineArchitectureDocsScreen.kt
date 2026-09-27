package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CyanTelemetry
import com.example.ui.theme.RailBorderSteel
import com.example.ui.theme.RailCardDark
import com.example.ui.theme.RailSurfaceDark
import com.example.ui.theme.SignalGreen

@Composable
fun EngineArchitectureDocsScreen(
    onLaunchSingleFileWebGlMode: () -> Unit,
    onRunSelfTestNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    val qaChecklist = listOf(
        "1. [60 FPS Locked & 1% Low < 20ms]: Open F3 overlay during Freight Haul scenario for 5 minutes; verify Avg FPS = 60.0 and 1% Low <= 19.8 ms.",
        "2. [Peak RAM < 350 MB & Flat Graph]: Run 30s Benchmark and soak test; verify VM/JS heap stays < 180 MB (budget 350 MB) with 0 upward drift.",
        "3. [Zero Uncaught Exceptions]: Cycle Free Roam, Passenger Run, and Freight Haul + F9 Self-Test; verify 0 uncaught errors.",
        "4. [Cold Load < 8 Seconds]: Verify all 35 procedural assets (5 Locos, 10 Cars, 20 Scenery) and PCM audio buffers initialize in < 1.2s before first frame.",
        "5. [Input Latency < 50ms]: Tap W/S or Throttle Notch N1-N8; verify immediate state transition on next 16.67ms tick.",
        "6. [Deterministic 60Hz Physics]: Save state in Slot 1, reload Slot 1 with identical seed (1337420) & input log; verify bit-identical train position.",
        "7. [Draw Calls < 80 & Triangles < 100k]: Open F3 overlay in Chase Cam with 12-car Freight Haul; verify Draw Calls <= 74 and Triangles <= 89,000.",
        "8. [Procedural Texture Budget < 40 MB]: Verify all textures/liveries are procedurally generated at <= 256x256 resolution (< 8 MB total VRAM).",
        "9. [Wheel-Slip & O(1) Coupler Slack]: Set Notch 8 on steep grade; verify wheel-slip indicator, slip audio, and O(1) coupler extension telemetry.",
        "10. [Surveyor 5° Snap & 20-Step Pooled Undo]: Switch to Surveyor Mode, place track nodes & scenery, rotate in 5° steps, and test Undo/Redo.",
        "11. [Colourblind Signal Shapes]: Verify CLEAR=Green Square, APPROACH=Yellow Diamond, and STOP=Red Circle in both HUD and 3D mast.",
        "12. [Adaptive Quality Governor Hysteresis]: Verify automatic tier step-down on >16/20/24ms frame spikes and 10s hysteresis recovery."
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header & Launch Single-File WebGL Engine Button
        Card(
            colors = CardDefaults.cardColors(containerColor = RailSurfaceDark),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, RailBorderSteel, RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "ENGINE ARCHITECTURE, QA CHECKLIST & ASSET GUIDE",
                    style = MaterialTheme.typography.titleLarge,
                    color = AmberGold
                )
                Text(
                    text = "Includes both the native 60Hz deterministic Kotlin engine and the complete single-file index.html WebGL/Canvas simulator.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onLaunchSingleFileWebGlMode,
                        colors = ButtonDefaults.buttonColors(containerColor = AmberGold, contentColor = Color.Black),
                        modifier = Modifier.weight(1f).testTag("btn_launch_single_file_html")
                    ) {
                        Icon(Icons.Default.Code, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Run Single-File index.html")
                    }
                    Button(
                        onClick = onRunSelfTestNow,
                        colors = ButtonDefaults.buttonColors(containerColor = SignalGreen, contentColor = Color.Black),
                        modifier = Modifier.weight(1f).testTag("btn_docs_run_f9")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Run F9 Self-Test")
                    }
                }
            }
        }

        // 1. Architecture Outline mapped to 4 GB / 60 FPS constraints
        Card(
            colors = CardDefaults.cardColors(containerColor = RailSurfaceDark),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, RailBorderSteel, RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("1. Architecture Contract (10 Modules -> 4 GB / 60 FPS)", style = MaterialTheme.typography.titleMedium, color = CyanTelemetry)
                Text("• CONFIG (Frozen): Centralizes all physics constants, 400m fog, 80m/200m LOD thresholds, and pool sizes.", style = MaterialTheme.typography.bodyMedium)
                Text("• Engine & Fixed 60Hz Loop: Delta accumulator capped at 250ms with render alpha interpolation.", style = MaterialTheme.typography.bodyMedium)
                Text("• Physics (Deterministic): Mulberry32 seeded PRNG, Davis rolling resistance, grade/curve forces, wheel-slip, O(1) coupler slack.", style = MaterialTheme.typography.bodyMedium)
                Text("• Renderer & Pools: Zero `new` in update/render; pre-allocated 64-particle pool, 120-tie pool, flat dark ellipse blob shadows.", style = MaterialTheme.typography.bodyMedium)
                Text("• Audio (Zero-Stutter): Pre-synthesized PCM buffers (Horn chord, Bell, Wheel-Slip, Brake Squeal, Coupler Clank).", style = MaterialTheme.typography.bodyMedium)
                Text("• SaveSystem & Determinism: 3 Room/JSON slots (<200KB) storing schema version, seed, tick count, and input event log.", style = MaterialTheme.typography.bodyMedium)
            }
        }

        // 2. 12-Item Measurable QA Checklist
        Card(
            colors = CardDefaults.cardColors(containerColor = RailSurfaceDark),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, RailBorderSteel, RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("2. 12-Test Measurable QA Checklist", style = MaterialTheme.typography.titleMedium, color = SignalGreen)
                qaChecklist.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(RailCardDark)
                            .padding(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SignalGreen)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = item, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        // 3. Asset Extension Guide & Known Limitations
        Card(
            colors = CardDefaults.cardColors(containerColor = RailSurfaceDark),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, RailBorderSteel, RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("3. Asset Extension Guide & Budgets", style = MaterialTheme.typography.titleMedium, color = AmberGold)
                Text(
                    text = "To add a new Locomotive or Scenery item inside HardcodedAssetLibrary:\n" +
                        "1. Keep Locomotives <= 800 tris, Freight Cars <= 400 tris, Scenery LOD0 <= 150 tris.\n" +
                        "2. Register the spec in TrainSimConfig.kt (locomotives / freightCars / sceneryItems).\n" +
                        "3. Ensure procedural canvas textures never exceed CONFIG.TEXTURE_MAX_RES (256x256).\n" +
                        "4. Verify total active scene triangles remain below CONFIG.MAX_TRIANGLES_BUDGET (100,000).",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text("4. Known Limitations & Tuning Escalation Ladder", style = MaterialTheme.typography.titleMedium, color = CyanTelemetry)
                Text(
                    text = "Sacrificed for 4GB / Intel HD 4000 60 FPS lock: Real-time shadow maps (replaced with parented ellipse blob shadows), PBR MeshStandardMaterial (replaced with Lambert/Flat shading), and dynamic GLTF/FBX streaming. On high-end GPUs, shadow cascades and 1024x1024 normal maps can be re-enabled.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
