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
        "1. [3D Cab View Animated Levers & Gauges]: Switch to Cab Interior 3D (C); verify Throttle Lever moves at every notch (N0–N8), Train/Ind Brake handles slide, and Speedometer/Brake Pipe needles rotate smoothly.",
        "2. [SPAD Red Signal Penalty Brake]: Drive past a Red (STOP) block signal at > 5 km/h; verify penalty emergency brake triggers within 1 second (Throttle -> N0, Train Brake -> 100%) with HUD alert.",
        "3. [Detailed 3D Locomotives & Rotating Wheels]: Switch to Chase Cam 3D or Free-Roam Orbit and drag to orbit; verify 2,950–4,120 triangle multi-part locomotive body, bogies, rotating spoked wheels, couplers, handrails, and number plate.",
        "4. [Time of Day Slider (Dawn / Day / Dusk / Night)]: Cycle Time of Day; verify Night mode illuminates twin locomotive headlights, passenger coach interior windows, town buildings, and platform lamps.",
        "5. [Weather & Adhesion (Rain, Sand & Wipers)]: Select Light Rain; verify falling rain particles, wet specular rail reflection, reduced adhesion wheel-slip on grades, Sand restoring grip, and animated Windshield Wipers.",
        "6. [5 Camera Modes (Cycle with C)]: Cycle through Cab Interior 3D, Chase Cam 3D, Free-Roam Orbit (drag to rotate), Trackside Signal Cam, and Cinematic Fly-By.",
        "7. [4 Sessions Including Interactive Tutorial]: Select Tutorial, Passenger Service (6 timed stops), Freight Run (1,480t consist on 2.2% grade), and Free Drive; verify step-by-step coaching and station dwell scoring.",
        "8. [60 FPS & Quality Presets (Low / Medium / High / Ultra)]: Open F3 overlay; verify 60 FPS target, real-time shadow projection under train, and draw distance scaling.",
        "9. [Surveyor Mode Track Splines & Scenery]: Switch to Surveyor Mode; place track spline nodes and 20 scenery items (depots, footbridges, catenary, trees) with 5° snap and 20-step Undo/Redo.",
        "10. [AI Rail Studio (8 Aspect Ratios + Image Edit + Veo 3.1 Video)]: Generate & edit locomotive liveries with Gemini 3.1 Flash / 3 Pro, apply directly to the 3D locomotive, and animate photos with Veo 3.1 (16:9 & 9:16)."
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = RailSurfaceDark),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, RailBorderSteel, RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "TRAINZ: A NEW ERA — 3D ARCHITECTURE & QA",
                    style = MaterialTheme.typography.titleLarge,
                    color = AmberGold
                )
                Text(
                    text = "Includes both the native 3D multi-mesh simulator and the self-contained single-file Three.js/WebGL2 engine (assets/index.html).",
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
                        Text("Open WebGL2 index.html")
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

        // 1. 12-Module Architecture Outline
        Card(
            colors = CardDefaults.cardColors(containerColor = RailSurfaceDark),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, RailBorderSteel, RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("1. 12-Module Architecture Outline (Trainz: A New Era)", style = MaterialTheme.typography.titleMedium, color = CyanTelemetry)
                Text("• Engine: Fixed 60 Hz simulation accumulator + interpolated 3D render pipeline & context-loss recovery.", style = MaterialTheme.typography.bodyMedium)
                Text("• Physics: Deterministic Mulberry32 PRNG, Davis resistance, grade/curve forces, Diesel/Electric/Steam power curves, Rain/Sand adhesion, and O(1) coupler slack.", style = MaterialTheme.typography.bodyMedium)
                Text("• Train: Detailed 3D locomotives (2,950–4,120 tris with bogies, rotating spoked wheels, couplers, handrails, roof fans, headlights) + 10 coaches/wagons + animated 3D Cab interior.", style = MaterialTheme.typography.bodyMedium)
                Text("• Route & Scenery: 8 km Alpine & Valley route with 3D ballasted track, sleepers, overhead catenary portals/wires, rolling hills, forests, rivers, and towns.", style = MaterialTheme.typography.bodyMedium)
                Text("• Signals & Sessions: Working block signals (colour-light + moving semaphore arm), automatic SPAD red-signal penalty brake, and 4 sessions (Free Drive, Passenger, Freight, Tutorial).", style = MaterialTheme.typography.bodyMedium)
                Text("• UI, Audio, Surveyor, SaveSystem & Benchmark: 5 cameras, Time of Day (Dawn/Day/Dusk/Night), Weather (Clear/Overcast/Rain/Fog), Quality presets, Web Audio / PCM synth, Surveyor 5° grid, Room DB saves, and F3/F9 diagnostics.", style = MaterialTheme.typography.bodyMedium)
            }
        }

        // 2. 10-Test Measurable QA Checklist
        Card(
            colors = CardDefaults.cardColors(containerColor = RailSurfaceDark),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, RailBorderSteel, RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("2. 10-Test Measurable QA Checklist", style = MaterialTheme.typography.titleMedium, color = SignalGreen)
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

        // 3. Extension Guide & Known Limitations
        Card(
            colors = CardDefaults.cardColors(containerColor = RailSurfaceDark),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, RailBorderSteel, RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("3. Guide for Adding a New Locomotive or Route", style = MaterialTheme.typography.titleMedium, color = AmberGold)
                Text(
                    text = "1. Add a new LocomotiveSpec (2,000–4,000 tris, tractionType = Diesel-Electric / Electric / Steam) to HardcodedAssetLibrary in TrainSimConfig.kt.\n" +
                        "2. Or generate a custom livery in the AI Rail Studio tab and tap 'Apply Livery to 3D Train' to skin the active 3D locomotive immediately.\n" +
                        "3. To add a new route or session, add station stops to ScenarioId or place spline nodes and buildings in Surveyor Mode.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text("4. Known Limitations & Next Improvements", style = MaterialTheme.typography.titleMedium, color = CyanTelemetry)
                Text(
                    text = "All 3D geometry and textures are procedurally constructed in-memory so cold load stays under 2 seconds with zero external downloads. Next steps include multi-track junction switches in Driver Mode and AI-controlled oncoming traffic on parallel tracks.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
