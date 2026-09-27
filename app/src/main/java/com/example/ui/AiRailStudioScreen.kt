package com.example.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ai.AiImageResult
import com.example.ai.AiVideoResult
import com.example.ai.GeminiRailStudioService
import com.example.data.GeneratedRailMediaEntity
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CyanTelemetry
import com.example.ui.theme.RailBorderSteel
import com.example.ui.theme.RailCardDark
import com.example.ui.theme.RailSurfaceDark
import com.example.ui.theme.SignalGreen
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiRailStudioScreen(
    currentImageResult: AiImageResult?,
    currentVideoResult: AiVideoResult?,
    isBusy: Boolean,
    statusBanner: String,
    savedMediaHistory: List<GeneratedRailMediaEntity>,
    onGenerateImage: (prompt: String, aspectRatio: String, studioQuality: Boolean) -> Unit,
    onEditImage: (sourceBitmap: Bitmap, editPrompt: String, aspectRatio: String) -> Unit,
    onAnimateVeoVideo: (sourceBitmap: Bitmap, motionPrompt: String, aspectRatio: String) -> Unit,
    onApplyLiveryFromBitmap: (Bitmap) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var createPrompt by remember {
        mutableStateOf("Heavy amber-and-slate diesel freight locomotive crossing an alpine viaduct at dusk, dramatic lighting")
    }
    var editPrompt by remember {
        mutableStateOf("Add winter snowstorm weather, glowing ditch lights, and weathered heritage railway decals")
    }
    var veoMotionPrompt by remember {
        mutableStateOf("Locomotive accelerating down the mountain pass with volumetric steam plumes and glowing track signals")
    }

    var selectedImageAspectRatio by remember { mutableStateOf("16:9") }
    var studioQualityPro by remember { mutableStateOf(false) }
    var selectedVideoAspectRatio by remember { mutableStateOf("16:9") }
    var uploadedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Zero-permission Android Photo Picker (ActivityResultContracts.PickVisualMedia)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val decoded = BitmapFactory.decodeStream(stream)
                    if (decoded != null) {
                        uploadedBitmap = decoded
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Hero Banner Card
        Card(
            colors = CardDefaults.cardColors(containerColor = RailSurfaceDark),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, RailBorderSteel, RoundedCornerShape(14.dp))
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(140.dp)) {
                Image(
                    painter = painterResource(id = R.drawable.img_hero_trainz_1790490256097),
                    contentDescription = "IronRail 3D AI Studio Hero Banner",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xAA0B0F17))
                        .padding(14.dp),
                    contentAlignment = Alignment.BottomStart
                ) {
                    Column {
                        Text(
                            text = "AI RAIL LIVERY, PHOTO & VEO VIDEO STUDIO",
                            style = MaterialTheme.typography.titleLarge,
                            color = AmberGold
                        )
                        Text(
                            text = "Create & edit locomotive liveries with 8 aspect ratios (Gemini 3.1 Flash / 3 Pro) and animate rail photos with Veo 3.1.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        // Security Warning Notice (Required by Gemini API / Secret Management Skill for prototypes)
        Surface(
            color = Color(0xFF231B12),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, AmberGold.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
        ) {
            Text(
                text = stringResource(id = R.string.security_warning_prototype),
                style = MaterialTheme.typography.labelSmall,
                color = AmberGold,
                modifier = Modifier.padding(10.dp)
            )
        }

        // Status & Busy Indicator
        if (isBusy || statusBanner.isNotBlank()) {
            Surface(
                color = RailCardDark,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, CyanTelemetry, RoundedCornerShape(10.dp))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            color = AmberGold,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Text(
                        text = statusBanner,
                        style = MaterialTheme.typography.labelMedium,
                        color = CyanTelemetry
                    )
                }
            }
        }

        // SECTION 1: CREATE RAIL IMAGES & CONTROL ASPECT RATIOS (1:1, 2:3, 3:2, 3:4, 4:3, 9:16, 16:9, 21:9)
        Card(
            colors = CardDefaults.cardColors(containerColor = RailSurfaceDark),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, RailBorderSteel, RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Brush, contentDescription = null, tint = AmberGold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "1. Generate Rail Livery / Scene (8 Aspect Ratios)",
                        style = MaterialTheme.typography.titleMedium,
                        color = AmberGold
                    )
                }

                // Studio Quality Toggle (gemini-3.1-flash-image-preview vs gemini-3-pro-image-preview)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (studioQualityPro) {
                                "Model: gemini-3-pro-image-preview (Studio Quality)"
                            } else {
                                "Model: gemini-3.1-flash-image-preview (General Fast)"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = SignalGreen
                        )
                        Text(
                            text = "Toggle Studio Quality for high-detail locomotive liveries",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = studioQualityPro,
                        onCheckedChange = { studioQualityPro = it },
                        modifier = Modifier.testTag("switch_studio_quality_model")
                    )
                }

                // Aspect Ratio Chips (1:1, 2:3, 3:2, 3:4, 4:3, 9:16, 16:9, 21:9)
                Text("Select Aspect Ratio:", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    GeminiRailStudioService.SUPPORTED_IMAGE_ASPECT_RATIOS.forEach { ratio ->
                        FilterChip(
                            selected = selectedImageAspectRatio == ratio,
                            onClick = { selectedImageAspectRatio = ratio },
                            label = { Text(ratio, style = MaterialTheme.typography.labelMedium) },
                            modifier = Modifier.testTag("aspect_ratio_chip_${ratio.replace(":", "_")}")
                        )
                    }
                }

                OutlinedTextField(
                    value = createPrompt,
                    onValueChange = { createPrompt = it },
                    label = { Text("Image Generation Prompt") },
                    modifier = Modifier.fillMaxWidth().testTag("input_create_image_prompt")
                )

                Button(
                    onClick = {
                        onGenerateImage(createPrompt, selectedImageAspectRatio, studioQualityPro)
                    },
                    enabled = !isBusy && createPrompt.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = AmberGold, contentColor = Color.Black),
                    modifier = Modifier.fillMaxWidth().testTag("btn_generate_rail_image")
                ) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate Image ($selectedImageAspectRatio)")
                }
            }
        }

        // SECTION 2: UPLOAD PHOTO & EDIT IMAGES (gemini-3.1-flash-image-preview)
        Card(
            colors = CardDefaults.cardColors(containerColor = RailSurfaceDark),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, RailBorderSteel, RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Palette, contentDescription = null, tint = CyanTelemetry)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "2. Upload Photo & Edit Image (gemini-3.1-flash-image-preview)",
                        style = MaterialTheme.typography.titleMedium,
                        color = CyanTelemetry
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RailCardDark),
                        modifier = Modifier.weight(1f).testTag("btn_upload_photo_picker")
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Upload Photo")
                    }

                    val activeBmp = uploadedBitmap ?: currentImageResult?.bitmap
                    if (activeBmp != null) {
                        Button(
                            onClick = { onApplyLiveryFromBitmap(activeBmp) },
                            colors = ButtonDefaults.buttonColors(containerColor = SignalGreen, contentColor = Color.Black),
                            modifier = Modifier.weight(1f).testTag("btn_apply_livery_to_loco")
                        ) {
                            Text("Apply Livery to 3D Train")
                        }
                    }
                }

                // Active Image Preview
                val previewBitmap = uploadedBitmap ?: currentImageResult?.bitmap
                if (previewBitmap != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black)
                            .border(1.dp, RailBorderSteel, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = previewBitmap.asImageBitmap(),
                            contentDescription = "Selected or Generated Rail Image",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                OutlinedTextField(
                    value = editPrompt,
                    onValueChange = { editPrompt = it },
                    label = { Text("Image Edit Prompt (gemini-3.1-flash-image-preview)") },
                    modifier = Modifier.fillMaxWidth().testTag("input_edit_image_prompt")
                )

                Button(
                    onClick = {
                        val baseBmp = uploadedBitmap ?: currentImageResult?.bitmap
                            ?: GeminiRailStudioService.renderProceduralLiveryBitmap(createPrompt, selectedImageAspectRatio, false)
                        onEditImage(baseBmp, editPrompt, selectedImageAspectRatio)
                    },
                    enabled = !isBusy && editPrompt.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = CyanTelemetry, contentColor = Color.Black),
                    modifier = Modifier.fillMaxWidth().testTag("btn_edit_rail_image")
                ) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Edit Image with Gemini 3.1 Flash")
                }
            }
        }

        // SECTION 3: ANIMATE UPLOADED/GENERATED PHOTO INTO VIDEO (veo-3.1-fast-generate-preview, 16:9 or 9:16)
        Card(
            colors = CardDefaults.cardColors(containerColor = RailSurfaceDark),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, RailBorderSteel, RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MovieCreation, contentDescription = null, tint = SignalGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "3. Animate Photo into Video (veo-3.1-fast-generate-preview)",
                        style = MaterialTheme.typography.titleMedium,
                        color = SignalGreen
                    )
                }

                Text(
                    text = "Select Veo Video Aspect Ratio (16:9 Landscape or 9:16 Portrait):",
                    style = MaterialTheme.typography.labelMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedVideoAspectRatio == "16:9",
                        onClick = { selectedVideoAspectRatio = "16:9" },
                        label = { Text("16:9 (Landscape)") },
                        modifier = Modifier.testTag("veo_ratio_16_9")
                    )
                    FilterChip(
                        selected = selectedVideoAspectRatio == "9:16",
                        onClick = { selectedVideoAspectRatio = "9:16" },
                        label = { Text("9:16 (Portrait)") },
                        modifier = Modifier.testTag("veo_ratio_9_16")
                    )
                }

                OutlinedTextField(
                    value = veoMotionPrompt,
                    onValueChange = { veoMotionPrompt = it },
                    label = { Text("Veo Video Animation Prompt") },
                    modifier = Modifier.fillMaxWidth().testTag("input_veo_motion_prompt")
                )

                Button(
                    onClick = {
                        val sourceBmp = uploadedBitmap ?: currentImageResult?.bitmap
                            ?: GeminiRailStudioService.renderProceduralLiveryBitmap(createPrompt, selectedVideoAspectRatio, false)
                        onAnimateVeoVideo(sourceBmp, veoMotionPrompt, selectedVideoAspectRatio)
                    },
                    enabled = !isBusy && veoMotionPrompt.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = SignalGreen, contentColor = Color.Black),
                    modifier = Modifier.fillMaxWidth().testTag("btn_animate_veo_video")
                ) {
                    Icon(Icons.Default.MovieCreation, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Animate Photo with Veo 3.1 ($selectedVideoAspectRatio)")
                }

                // Animated Video Playback Viewport
                if (currentVideoResult != null && currentVideoResult.previewFrames.isNotEmpty()) {
                    var frameIdx by remember(currentVideoResult) { mutableIntStateOf(0) }
                    LaunchedEffect(currentVideoResult) {
                        while (true) {
                            delay(120)
                            frameIdx = (frameIdx + 1) % currentVideoResult.previewFrames.size
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black)
                            .border(1.dp, SignalGreen, RoundedCornerShape(10.dp))
                            .padding(8.dp)
                            .testTag("veo_video_player_box")
                    ) {
                        Text(
                            text = currentVideoResult.statusNote,
                            style = MaterialTheme.typography.labelSmall,
                            color = SignalGreen
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Image(
                            bitmap = currentVideoResult.previewFrames[frameIdx].asImageBitmap(),
                            contentDescription = "Veo 3.1 Generated Video Playback",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (currentVideoResult.aspectRatio == "9:16") 280.dp else 190.dp)
                        )
                    }
                }
            }
        }

        // Saved Media History from Room DB
        if (savedMediaHistory.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = RailSurfaceDark),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Recent Studio Creations (${savedMediaHistory.size})", style = MaterialTheme.typography.titleMedium, color = AmberGold)
                    savedMediaHistory.take(5).forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(RailCardDark)
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "[${item.mediaType}] ${item.aspectRatio} • ${item.modelUsed}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CyanTelemetry
                                )
                                Text(
                                    text = item.prompt,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
