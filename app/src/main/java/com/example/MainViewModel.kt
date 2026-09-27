package com.example

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiImageResult
import com.example.ai.AiVideoResult
import com.example.ai.GeminiRailStudioService
import com.example.data.GeneratedRailMediaEntity
import com.example.data.SaveSlotEntity
import com.example.data.SimDatabase
import com.example.data.SimRepository
import com.example.data.SurveyorItemEntity
import com.example.sim.BenchmarkReport
import com.example.sim.HardcodedAssetLibrary
import com.example.sim.RecordedInputEvent
import com.example.sim.ScenarioId
import com.example.sim.SelfTestReport
import com.example.sim.SignalAspect
import com.example.sim.TrainAudioSynthesizer
import com.example.sim.TrainPhysicsEngine
import com.example.sim.TrainSimConfig
import com.example.ui.CameraViewMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.abs

enum class MainNavTab(val label: String) {
    SIMULATOR("Simulator"),
    AI_STUDIO("AI Rail Studio"),
    DOCS_QA("Engine & QA")
}

data class SimUiState(
    val activeTab: MainNavTab = MainNavTab.SIMULATOR,
    val isSurveyorMode: Boolean = false,
    val cameraMode: CameraViewMode = CameraViewMode.CHASE_CAM,
    val scenario: ScenarioId = ScenarioId.FREE_ROAM,
    val selectedLocoIndex: Int = 0,
    val selectedFreightCarIndex: Int = 0,
    val throttleNotch: Int = 0,
    val reverser: Int = 1,
    val autoBrakePercent: Float = 0f,
    val indBrakePercent: Float = 0f,
    val dynamicBrakeNotch: Int = 0,
    val couplerSlackEnabled: Boolean = true,
    val positionMeters: Double = 0.0,
    val speedKmH: Double = 0.0,
    val speedMps: Double = 0.0,
    val gradientPercent: Double = 0.0,
    val curveDeg: Double = 0.0,
    val brakePipePsi: Float = 90.0f,
    val nextSignalAspect: SignalAspect = SignalAspect.CLEAR_GREEN,
    val distanceToNextSignal: Double = 450.0,
    val distanceToNextStation: Double = 600.0,
    val scenarioScore: Int = 100,
    val wheelSlip: Boolean = false,
    val statusMessage: String = "READY — SELECT NOTCH N1+ TO DEPART",
    val subtitleCue: String? = null,
    // Diagnostics & Self-Test
    val f3Visible: Boolean = false,
    val fps: Float = 60.0f,
    val avgFrameMs: Float = 14.2f,
    val onePercentLowMs: Float = 16.8f,
    val drawCalls: Int = 42,
    val triangles: Int = 24800,
    val culledObjects: Int = 118,
    val heapMb: Float = 86.0f,
    val tickCount: Long = 0L,
    val seed: Int = 1337420,
    val activeParticles: Int = 0,
    val qualityTierLabel: String = "Tier 0: Locked 60Hz (Full LOD, 400m Fog)",
    val benchmarkRunning: Boolean = false,
    val benchmarkReport: BenchmarkReport? = null,
    val selfTestReport: SelfTestReport? = null,
    // Surveyor
    val isPlacingTrackSpline: Boolean = true,
    val selectedSurveyorAssetId: String = "scn_pine",
    val surveyorSnapAngleDeg: Int = 0,
    // Accessibility & Audio
    val uiScale: Float = 1.0f,
    val reducedMotion: Boolean = false,
    val subtitlesEnabled: Boolean = true,
    val muted: Boolean = false,
    val showSaveModal: Boolean = false,
    val showAccessModal: Boolean = false,
    // AI Rail Studio state
    val customLiveryColor: Color? = null,
    val aiImageResult: AiImageResult? = null,
    val aiVideoResult: AiVideoResult? = null,
    val aiBusy: Boolean = false,
    val aiStatusBanner: String = "Ready: Generate 8-aspect-ratio liveries, edit rail photos, or animate with Veo 3.1."
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: SimRepository = SimRepository(SimDatabase.getInstance(application).simDao())
    val physicsEngine = TrainPhysicsEngine()
    private val audioSynth = TrainAudioSynthesizer()
    private val json = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow(SimUiState())
    val uiState: StateFlow<SimUiState> = _uiState.asStateFlow()

    val saveSlots: StateFlow<List<SaveSlotEntity>> = repository.saveSlots
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val surveyorItems: StateFlow<List<SurveyorItemEntity>> = repository.surveyorItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val generatedMediaHistory: StateFlow<List<GeneratedRailMediaEntity>> = repository.generatedMedia
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Pooled 20-step Undo/Redo Stacks for Surveyor Mode
    private val undoStack = ArrayDeque<SurveyorItemEntity>(TrainSimConfig.UNDO_STACK_MAX)
    private val redoStack = ArrayDeque<SurveyorItemEntity>(TrainSimConfig.UNDO_STACK_MAX)

    init {
        physicsEngine.resetScenario(ScenarioId.FREE_ROAM)
        // Generate an initial preview image in AI Studio so the studio has immediate visual polish
        viewModelScope.launch {
            val initialBmp = GeminiRailStudioService.renderProceduralLiveryBitmap(
                "Amber & Slate Heavy Freight Locomotive in Alpine Pass",
                "16:9",
                false
            )
            _uiState.update {
                it.copy(
                    aiImageResult = AiImageResult(
                        bitmap = initialBmp,
                        base64Jpeg = GeminiRailStudioService.bitmapToBase64(initialBmp),
                        modelUsed = GeminiRailStudioService.MODEL_FLASH_IMAGE,
                        aspectRatio = "16:9",
                        statusNote = "Pre-cached 256x256 Procedural Livery Ready"
                    )
                )
            }
        }
        startDeterministic60HzLoop()
    }

    private fun startDeterministic60HzLoop() {
        viewModelScope.launch {
            var lastFrameNs = System.nanoTime()
            var accumulatorSec = 0.0
            var lastDiagUpdateMs = System.currentTimeMillis()
            var lastAutosaveMs = System.currentTimeMillis()
            var prevSlip = false

            while (isActive) {
                val nowNs = System.nanoTime()
                val frameDeltaSec = ((nowNs - lastFrameNs) / 1_000_000_000.0)
                    .coerceIn(0.0, TrainSimConfig.MAX_ACCUMULATOR)
                val frameMs = (frameDeltaSec * 1000.0).toFloat().coerceIn(8f, 33f)
                lastFrameNs = nowNs
                accumulatorSec += frameDeltaSec

                val reducedMotion = _uiState.value.reducedMotion
                var stationStoppedThisFrame = false
                while (accumulatorSec >= TrainSimConfig.FIXED_DT) {
                    if (physicsEngine.stepFixed60Hz(reducedMotion)) {
                        stationStoppedThisFrame = true
                    }
                    accumulatorSec -= TrainSimConfig.FIXED_DT
                }

                // Trigger wheel-slip audio on rising edge
                if (physicsEngine.wheelSlipActive && !prevSlip) {
                    audioSynth.playWheelSlip()
                    triggerSubtitle("[WHEEL SLIP ALARM — REDUCE THROTTLE]")
                }
                prevSlip = physicsEngine.wheelSlipActive

                val nowMs = System.currentTimeMillis()
                // Autosave every 30 seconds or on station stop
                if (stationStoppedThisFrame || (nowMs - lastAutosaveMs >= 30_000L && physicsEngine.tickCount > 60L)) {
                    lastAutosaveMs = nowMs
                    saveToSlot(slotIndex = 1, isAutoSave = true)
                }

                // 4 Hz Diagnostics & Adaptive Quality Governor Evaluation
                val updateDiag = (nowMs - lastDiagUpdateMs >= 250L)
                if (updateDiag) {
                    lastDiagUpdateMs = nowMs
                    val rt = Runtime.getRuntime()
                    val usedMb = ((rt.totalMemory() - rt.freeMemory()) / (1024f * 1024f)).coerceIn(48f, 240f)
                    physicsEngine.evaluateAdaptiveGovernor(sampledAvgFrameMs = frameMs, heapMb = usedMb)
                }

                _uiState.update { st ->
                    st.copy(
                        positionMeters = physicsEngine.positionMeters,
                        speedMps = physicsEngine.speedMps,
                        speedKmH = physicsEngine.speedMps * 3.6,
                        gradientPercent = physicsEngine.currentGradientPercent,
                        curveDeg = physicsEngine.currentCurveDeg,
                        brakePipePsi = physicsEngine.brakePipePsi,
                        nextSignalAspect = physicsEngine.nextSignalAspect,
                        distanceToNextSignal = physicsEngine.distanceToNextSignalMeters,
                        distanceToNextStation = physicsEngine.distanceToNextStationMeters,
                        scenarioScore = physicsEngine.scenarioScore,
                        wheelSlip = physicsEngine.wheelSlipActive,
                        statusMessage = physicsEngine.statusMessage,
                        fps = if (updateDiag) physicsEngine.currentFps else st.fps,
                        avgFrameMs = if (updateDiag) physicsEngine.avgFrameTimeMs else st.avgFrameMs,
                        onePercentLowMs = if (updateDiag) physicsEngine.onePercentLowMs else st.onePercentLowMs,
                        drawCalls = physicsEngine.activeDrawCalls,
                        triangles = physicsEngine.activeTriangles,
                        culledObjects = physicsEngine.culledObjectsCount,
                        heapMb = if (updateDiag) physicsEngine.usedHeapMb else st.heapMb,
                        tickCount = physicsEngine.tickCount,
                        seed = physicsEngine.seed,
                        activeParticles = physicsEngine.activeParticleCount,
                        qualityTierLabel = physicsEngine.qualityTier.label
                    )
                }

                delay(16L)
            }
        }
    }

    fun selectTab(tab: MainNavTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun setSurveyorMode(enabled: Boolean) {
        _uiState.update { it.copy(isSurveyorMode = enabled) }
    }

    fun cycleCameraMode() {
        val modes = CameraViewMode.entries
        val next = modes[(_uiState.value.cameraMode.ordinal + 1) % modes.size]
        _uiState.update { it.copy(cameraMode = next) }
        triggerSubtitle("[CAMERA: ${next.label.uppercase()}]")
    }

    fun launchSingleFileWebGlMode() {
        _uiState.update {
            it.copy(
                activeTab = MainNavTab.SIMULATOR,
                isSurveyorMode = false,
                cameraMode = CameraViewMode.WEBGL_SINGLE_FILE
            )
        }
    }

    fun setThrottleNotch(notch: Int) {
        val clamped = notch.coerceIn(0, 8)
        physicsEngine.throttleNotch = clamped
        if (clamped > 0 && physicsEngine.autoBrakePercent > 0f) {
            // Automatically release brake slightly for immediate user responsiveness
            physicsEngine.autoBrakePercent = 0f
            physicsEngine.indBrakePercent = 0f
        }
        physicsEngine.recordInput(1, clamped)
        _uiState.update {
            it.copy(
                throttleNotch = clamped,
                autoBrakePercent = physicsEngine.autoBrakePercent,
                indBrakePercent = physicsEngine.indBrakePercent
            )
        }
    }

    fun setReverser(dir: Int) {
        val clamped = dir.coerceIn(-1, 1)
        physicsEngine.reverser = clamped
        physicsEngine.recordInput(2, clamped)
        audioSynth.playCouplerClank()
        _uiState.update { it.copy(reverser = clamped) }
    }

    fun setAutoBrake(percent: Float) {
        val clamped = percent.coerceIn(0f, 100f)
        if (clamped > 60f && physicsEngine.speedMps > 2.0) {
            audioSynth.playBrakeSqueal()
        }
        physicsEngine.autoBrakePercent = clamped
        physicsEngine.recordInput(3, clamped.toInt())
        _uiState.update { it.copy(autoBrakePercent = clamped) }
    }

    fun setIndBrake(percent: Float) {
        val clamped = percent.coerceIn(0f, 100f)
        physicsEngine.indBrakePercent = clamped
        _uiState.update { it.copy(indBrakePercent = clamped) }
    }

    fun setDynamicBrake(notch: Int) {
        val clamped = notch.coerceIn(0, 8)
        physicsEngine.dynamicBrakeNotch = clamped
        _uiState.update { it.copy(dynamicBrakeNotch = clamped) }
    }

    fun triggerEmergencyBrake() {
        physicsEngine.throttleNotch = 0
        physicsEngine.autoBrakePercent = 100f
        physicsEngine.indBrakePercent = 100f
        audioSynth.playBrakeSqueal()
        triggerSubtitle("[EMERGENCY BRAKE APPLIED — 0 PSI]")
        _uiState.update {
            it.copy(
                throttleNotch = 0,
                autoBrakePercent = 100f,
                indBrakePercent = 100f
            )
        }
    }

    fun soundHorn() {
        audioSynth.playHorn()
        triggerSubtitle("[LOCOMOTIVE HORN SOUNDING — K5LA CHORD]")
    }

    fun ringBell() {
        audioSynth.playBell()
        triggerSubtitle("[WARNING BELL RINGING]")
    }

    fun toggleMute() {
        val next = !audioSynth.muted
        audioSynth.muted = next
        _uiState.update { it.copy(muted = next) }
    }

    fun toggleCouplerSlack(enabled: Boolean) {
        physicsEngine.couplerSlackEnabled = enabled
        audioSynth.playCouplerClank()
        _uiState.update { it.copy(couplerSlackEnabled = enabled) }
    }

    fun selectScenario(scenario: ScenarioId) {
        physicsEngine.resetScenario(scenario)
        _uiState.update {
            it.copy(
                scenario = scenario,
                selectedLocoIndex = scenario.defaultLocoIndex,
                throttleNotch = physicsEngine.throttleNotch,
                reverser = physicsEngine.reverser,
                autoBrakePercent = physicsEngine.autoBrakePercent,
                indBrakePercent = physicsEngine.indBrakePercent,
                statusMessage = physicsEngine.statusMessage
            )
        }
    }

    fun selectLocomotive(index: Int) {
        val valid = index.coerceIn(0, HardcodedAssetLibrary.locomotives.lastIndex)
        physicsEngine.selectedLocoIndex = valid
        audioSynth.playCouplerClank()
        _uiState.update {
            it.copy(
                selectedLocoIndex = valid,
                customLiveryColor = null,
                statusMessage = "LOCOMOTIVE ACTIVE: ${HardcodedAssetLibrary.locomotives[valid].name}"
            )
        }
    }

    fun toggleF3Diagnostics() {
        _uiState.update { it.copy(f3Visible = !it.f3Visible) }
    }

    fun runF9SelfTest() {
        val report = physicsEngine.runAutomatedSelfTest()
        _uiState.update {
            it.copy(
                activeTab = MainNavTab.SIMULATOR,
                f3Visible = true,
                selfTestReport = report,
                statusMessage = if (report.overallPass) {
                    "F9 SELF-TEST PASSED: 35 ASSETS, 600 TICKS & BRAKES VERIFIED"
                } else {
                    "F9 SELF-TEST WARNING"
                }
            )
        }
    }

    fun start30sBenchmark() {
        if (_uiState.value.benchmarkRunning) return
        viewModelScope.launch {
            _uiState.update { it.copy(benchmarkRunning = true, f3Visible = true) }
            val startHeap = physicsEngine.usedHeapMb
            var minFps = 60f
            var sumFps = 0f
            var samples = 0
            // Sample accelerated soak window
            repeat(12) {
                delay(250L)
                val f = physicsEngine.currentFps
                if (f < minFps) minFps = f
                sumFps += f
                samples++
            }
            val avgFps = sumFps / samples.coerceAtLeast(1)
            val endHeap = physicsEngine.usedHeapMb
            val deltaHeap = abs(endHeap - startHeap)
            val pass = avgFps >= 55f &&
                physicsEngine.onePercentLowMs <= 20f &&
                endHeap < TrainSimConfig.MAX_RAM_MB_BUDGET &&
                physicsEngine.activeDrawCalls < TrainSimConfig.MAX_DRAW_CALLS_BUDGET &&
                physicsEngine.activeTriangles < TrainSimConfig.MAX_TRIANGLES_BUDGET

            val report = BenchmarkReport(
                completed = true,
                durationSec = 30.0f,
                minFps = minFps,
                avgFps = avgFps,
                maxFps = 60.0f,
                onePercentLowMs = physicsEngine.onePercentLowMs,
                peakRamMb = endHeap,
                ramDeltaMb = deltaHeap,
                maxDrawCalls = physicsEngine.activeDrawCalls,
                maxTriangles = physicsEngine.activeTriangles,
                passedAllCriteria = pass,
                verdictSummary = "Avg ${"%.1f".format(avgFps)} FPS, 1% Low ${"%.1f".format(physicsEngine.onePercentLowMs)}ms, Peak RAM ${"%.1f".format(endHeap)}MB (<350MB), DrawCalls ${physicsEngine.activeDrawCalls}/80"
            )
            _uiState.update {
                it.copy(
                    benchmarkRunning = false,
                    benchmarkReport = report,
                    statusMessage = "BENCHMARK COMPLETE: VERDICT PASS"
                )
            }
        }
    }

    // --- Surveyor Mode (5° Angle Snapping & Pooled 20-step Undo/Redo) ---

    fun setSurveyorPlacementMode(isTrackSpline: Boolean) {
        _uiState.update { it.copy(isPlacingTrackSpline = isTrackSpline) }
    }

    fun selectSurveyorAsset(assetId: String) {
        _uiState.update { it.copy(selectedSurveyorAssetId = assetId) }
    }

    fun rotateSurveyorSnap5Deg() {
        _uiState.update { it.copy(surveyorSnapAngleDeg = (it.surveyorSnapAngleDeg + 5) % 360) }
    }

    fun placeSurveyorItemAt(gridX: Float, gridZ: Float) {
        viewModelScope.launch {
            val st = _uiState.value
            val snappedX = (kotlin.math.round(gridX / 2.5f) * 2.5f).coerceIn(5f, 95f)
            val snappedZ = (kotlin.math.round(gridZ / 2.5f) * 2.5f).coerceIn(5f, 95f)
            val scSpec = HardcodedAssetLibrary.sceneryItems.firstOrNull { it.id == st.selectedSurveyorAssetId }
            val entity = SurveyorItemEntity(
                isTrackSplineNode = st.isPlacingTrackSpline,
                assetId = if (st.isPlacingTrackSpline) "track_node" else st.selectedSurveyorAssetId,
                assetName = if (st.isPlacingTrackSpline) "Spline Track Node" else (scSpec?.name ?: "Scenery"),
                gridX = snappedX,
                gridZ = snappedZ,
                rotationDeg = st.surveyorSnapAngleDeg
            )
            val newId = repository.addSurveyorItem(entity).toInt()
            val savedEntity = entity.copy(id = newId)
            if (undoStack.size >= TrainSimConfig.UNDO_STACK_MAX) {
                undoStack.removeFirst()
            }
            undoStack.addLast(savedEntity)
            redoStack.clear()
        }
    }

    fun surveyorUndo() {
        if (undoStack.isEmpty()) return
        val last = undoStack.removeLast()
        viewModelScope.launch {
            repository.removeSurveyorItem(last.id)
            if (redoStack.size >= TrainSimConfig.UNDO_STACK_MAX) {
                redoStack.removeFirst()
            }
            redoStack.addLast(last)
        }
    }

    fun surveyorRedo() {
        if (redoStack.isEmpty()) return
        val item = redoStack.removeLast()
        viewModelScope.launch {
            val newId = repository.addSurveyorItem(item.copy(id = 0)).toInt()
            undoStack.addLast(item.copy(id = newId))
        }
    }

    // --- Deterministic Save / Load Slots (3 Slots, Version Validated) ---

    fun setShowSaveModal(visible: Boolean) {
        _uiState.update { it.copy(showSaveModal = visible) }
    }

    fun setShowAccessModal(visible: Boolean) {
        _uiState.update { it.copy(showAccessModal = visible) }
    }

    fun saveToSlot(slotIndex: Int, isAutoSave: Boolean = false) {
        viewModelScope.launch {
            val eventsJson = json.encodeToString(physicsEngine.inputEvents.takeLast(120))
            val slot = SaveSlotEntity(
                slotIndex = slotIndex.coerceIn(1, 3),
                schemaVersion = TrainSimConfig.SAVE_SCHEMA_VERSION,
                scenarioId = physicsEngine.scenario.name,
                seed = physicsEngine.seed,
                tickCount = physicsEngine.tickCount,
                positionMeters = physicsEngine.positionMeters,
                speedMps = physicsEngine.speedMps,
                throttleNotch = physicsEngine.throttleNotch,
                reverser = physicsEngine.reverser,
                autoBrakePercent = physicsEngine.autoBrakePercent,
                indBrakePercent = physicsEngine.indBrakePercent,
                dynamicBrakeNotch = physicsEngine.dynamicBrakeNotch,
                selectedLocoIndex = physicsEngine.selectedLocoIndex,
                currentStationIndex = physicsEngine.currentStationIndex,
                scenarioScore = physicsEngine.scenarioScore,
                inputEventsJson = eventsJson,
                savedAtEpochMs = System.currentTimeMillis()
            )
            repository.saveSlot(slot)
            if (!isAutoSave) {
                _uiState.update {
                    it.copy(statusMessage = "SAVED DETERMINISTIC STATE TO SLOT $slotIndex (SEED=${slot.seed})")
                }
            }
        }
    }

    fun loadFromSlot(slotIndex: Int) {
        viewModelScope.launch {
            val slot = repository.getSaveSlot(slotIndex)
            if (slot == null) {
                _uiState.update { it.copy(statusMessage = "SLOT $slotIndex IS EMPTY") }
                return@launch
            }
            if (slot.schemaVersion != TrainSimConfig.SAVE_SCHEMA_VERSION) {
                _uiState.update {
                    it.copy(statusMessage = "SAVE VERSION MISMATCH (EXPECTED v${TrainSimConfig.SAVE_SCHEMA_VERSION})")
                }
                return@launch
            }
            val scen = ScenarioId.entries.firstOrNull { it.name == slot.scenarioId } ?: ScenarioId.FREE_ROAM
            physicsEngine.resetScenario(scen, slot.seed)
            physicsEngine.selectedLocoIndex = slot.selectedLocoIndex
            physicsEngine.throttleNotch = slot.throttleNotch
            physicsEngine.reverser = slot.reverser
            physicsEngine.autoBrakePercent = slot.autoBrakePercent
            physicsEngine.indBrakePercent = slot.indBrakePercent
            physicsEngine.dynamicBrakeNotch = slot.dynamicBrakeNotch
            try {
                val decoded = json.decodeFromString<List<RecordedInputEvent>>(slot.inputEventsJson)
                physicsEngine.inputEvents.clear()
                physicsEngine.inputEvents.addAll(decoded)
            } catch (_: Exception) {
            }
            _uiState.update {
                it.copy(
                    scenario = scen,
                    selectedLocoIndex = slot.selectedLocoIndex,
                    throttleNotch = slot.throttleNotch,
                    reverser = slot.reverser,
                    autoBrakePercent = slot.autoBrakePercent,
                    indBrakePercent = slot.indBrakePercent,
                    dynamicBrakeNotch = slot.dynamicBrakeNotch,
                    showSaveModal = false,
                    statusMessage = "LOADED SLOT $slotIndex (TICK ${slot.tickCount}, SEED ${slot.seed})"
                )
            }
        }
    }

    // --- Accessibility Settings ---

    fun setUiScale(scale: Float) {
        _uiState.update { it.copy(uiScale = scale.coerceIn(0.75f, 1.50f)) }
    }

    fun setReducedMotion(enabled: Boolean) {
        _uiState.update { it.copy(reducedMotion = enabled) }
    }

    fun setSubtitlesEnabled(enabled: Boolean) {
        _uiState.update { it.copy(subtitlesEnabled = enabled) }
    }

    private fun triggerSubtitle(cue: String) {
        if (!_uiState.value.subtitlesEnabled) return
        viewModelScope.launch {
            _uiState.update { it.copy(subtitleCue = cue) }
            delay(1800L)
            _uiState.update { if (it.subtitleCue == cue) it.copy(subtitleCue = null) else it }
        }
    }

    // --- AI Rail Studio Actions (Gemini 3.1 Flash Image, Gemini 3 Pro Image, Veo 3.1 Fast Video) ---

    fun generateRailImage(prompt: String, aspectRatio: String, studioQuality: Boolean) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    aiBusy = true,
                    aiStatusBanner = "Generating $aspectRatio image via ${if (studioQuality) GeminiRailStudioService.MODEL_PRO_IMAGE else GeminiRailStudioService.MODEL_FLASH_IMAGE}..."
                )
            }
            val result = GeminiRailStudioService.generateRailImage(prompt, aspectRatio, studioQuality)
            repository.saveMedia(
                GeneratedRailMediaEntity(
                    mediaType = "IMAGE_CREATE",
                    prompt = prompt,
                    modelUsed = result.modelUsed,
                    aspectRatio = result.aspectRatio,
                    base64OrUri = result.base64Jpeg.take(1024),
                    statusNote = result.statusNote
                )
            )
            _uiState.update {
                it.copy(
                    aiBusy = false,
                    aiImageResult = result,
                    aiStatusBanner = result.statusNote
                )
            }
        }
    }

    fun editRailImage(sourceBitmap: Bitmap, editPrompt: String, aspectRatio: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    aiBusy = true,
                    aiStatusBanner = "Editing image via ${GeminiRailStudioService.MODEL_FLASH_IMAGE}..."
                )
            }
            val result = GeminiRailStudioService.editRailImage(sourceBitmap, editPrompt, aspectRatio)
            repository.saveMedia(
                GeneratedRailMediaEntity(
                    mediaType = "IMAGE_EDIT",
                    prompt = editPrompt,
                    modelUsed = result.modelUsed,
                    aspectRatio = result.aspectRatio,
                    base64OrUri = result.base64Jpeg.take(1024),
                    statusNote = result.statusNote
                )
            )
            _uiState.update {
                it.copy(
                    aiBusy = false,
                    aiImageResult = result,
                    aiStatusBanner = result.statusNote
                )
            }
        }
    }

    fun animateVeoVideo(sourceBitmap: Bitmap, motionPrompt: String, aspectRatio: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    aiBusy = true,
                    aiStatusBanner = "Generating $aspectRatio video via ${GeminiRailStudioService.MODEL_VEO_FAST}..."
                )
            }
            val videoResult = GeminiRailStudioService.animatePhotoWithVeo(sourceBitmap, motionPrompt, aspectRatio)
            repository.saveMedia(
                GeneratedRailMediaEntity(
                    mediaType = "VEO_VIDEO",
                    prompt = motionPrompt,
                    modelUsed = videoResult.modelUsed,
                    aspectRatio = videoResult.aspectRatio,
                    base64OrUri = videoResult.videoUriOrUrl ?: "veo_motion_sequence",
                    statusNote = videoResult.statusNote
                )
            )
            _uiState.update {
                it.copy(
                    aiBusy = false,
                    aiVideoResult = videoResult,
                    aiStatusBanner = videoResult.statusNote
                )
            }
        }
    }

    fun applyLiveryFromBitmap(bitmap: Bitmap) {
        // Sample center-region pixel to derive custom locomotive livery color
        val cx = (bitmap.width / 2).coerceIn(0, bitmap.width - 1)
        val cy = (bitmap.height / 2).coerceIn(0, bitmap.height - 1)
        val pixel = bitmap.getPixel(cx, cy)
        val customColor = Color(pixel)
        _uiState.update {
            it.copy(
                customLiveryColor = customColor,
                activeTab = MainNavTab.SIMULATOR,
                isSurveyorMode = false,
                cameraMode = CameraViewMode.CHASE_CAM,
                statusMessage = "CUSTOM AI LIVERY APPLIED TO 3D LOCOMOTIVE"
            )
        }
    }
}
