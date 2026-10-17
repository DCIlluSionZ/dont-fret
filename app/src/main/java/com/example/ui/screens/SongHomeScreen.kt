package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AnalysisStage
import com.example.model.SongAnalysisProject
import com.example.network.GeminiModelOption
import com.example.ui.components.DciMetallicEmblemCard
import com.example.ui.theme.ChordAmber
import com.example.ui.theme.ConfidenceEmerald
import com.example.ui.theme.CyanHighlight
import com.example.ui.theme.DangerRose
import com.example.ui.theme.DciViolet
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.StudioBgDeep
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.TextIceWhite
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.DontFretUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SongHomeScreen(
    uiState: DontFretUiState,
    recentProjects: List<SongAnalysisProject>,
    onPickAudioUri: (Uri, String) -> Unit,
    onToggleMicRecording: () -> Unit,
    onUpdateLyricsInput: (String) -> Unit,
    onSelectModel: (GeminiModelOption) -> Unit,
    onRunAnalysis: () -> Unit,
    onOpenProject: (SongAnalysisProject) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showLyricsField by remember { mutableStateOf(false) }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = resolveFileName(context, uri)
            onPickAudioUri(uri, fileName)
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onToggleMicRecording()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // DCI Studio Banner Card
        DciMetallicEmblemCard()

        // Central Upload / Drop Area ("Drop in a song")
        Surface(
            color = StudioSurface,
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, ElectricBlue.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                StudioSurfaceElevated.copy(alpha = 0.65f),
                                StudioBgDeep.copy(alpha = 0.9f)
                            )
                        )
                    )
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Hero Drop Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(StudioBgDeep)
                        .border(
                            width = 2.dp,
                            brush = Brush.horizontalGradient(
                                colors = listOf(ElectricBlue, DciViolet, CyanHighlight)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable { audioPickerLauncher.launch("audio/*") }
                        .padding(vertical = 26.dp, horizontal = 16.dp)
                        .testTag("drop_in_song_box"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(ElectricBlue.copy(alpha = 0.16f))
                                .border(1.dp, CyanHighlight.copy(alpha = 0.5f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.UploadFile,
                                contentDescription = "Drop in a song",
                                tint = CyanHighlight,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Drop in a song",
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = TextIceWhite
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap to choose audio file • MP3 • WAV • M4A • AAC • FLAC",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 11.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )

                        if (uiState.selectedAudioFileName != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ConfidenceEmerald.copy(alpha = 0.16f))
                                    .border(1.dp, ConfidenceEmerald.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AudioFile,
                                    contentDescription = "Selected Audio File",
                                    tint = ConfidenceEmerald,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = uiState.selectedAudioFileName,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = ConfidenceEmerald
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Secondary Action Row: Record Guitar Mic + Paste Lyrics Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val isRec = uiState.isRecordingMic
                    Surface(
                        color = if (isRec) DangerRose.copy(alpha = 0.2f) else StudioBgDeep,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isRec) DangerRose else StudioBorder
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                if (isRec) {
                                    onToggleMicRecording()
                                } else {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                            .testTag("record_guitar_mic_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isRec) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = "Record Guitar or Audio",
                                tint = if (isRec) DangerRose else ChordAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isRec) "Stop (${uiState.recordingSeconds}s)" else "Record Guitar / Mic",
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (isRec) DangerRose else TextIceWhite
                            )
                        }
                    }

                    Surface(
                        color = if (showLyricsField || uiState.optionalLyricsInput.isNotBlank()) {
                            StudioSurfaceElevated
                        } else {
                            StudioBgDeep
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showLyricsField = !showLyricsField }
                            .testTag("toggle_paste_lyrics_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = "Paste Optional Lyrics",
                                tint = CyanHighlight,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (uiState.optionalLyricsInput.isNotBlank()) "Lyrics Attached ✓" else "Paste Lyrics (Opt)",
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = TextIceWhite
                            )
                        }
                    }
                }

                // Live Local DSP Tuner & Pitch Strip
                if (uiState.localDspResult != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = StudioBgDeep,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = "DSP Telemetry",
                                    tint = CyanHighlight,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "LOCAL DSP TUNER & HARMONIC TELEMETRY",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = CyanHighlight
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Detected Key: ${uiState.localDspResult.detectedKey}  •  Est. Tempo: ${uiState.localDspResult.estimatedBpm} BPM  •  ${uiState.localDspResult.tuningDescription}",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.sp,
                                color = ChordAmber
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = uiState.localDspResult.summaryReport,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 10.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }

                // Optional Lyrics Box
                AnimatedVisibility(visible = showLyricsField) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = uiState.optionalLyricsInput,
                            onValueChange = onUpdateLyricsInput,
                            label = {
                                Text("Paste song lyrics (optional — locks chords to exact syllables)")
                            },
                            placeholder = {
                                Text("Leave blank to let Gemini transcribe vocals automatically, or paste Suno / original lyrics here...")
                            },
                            minLines = 3,
                            maxLines = 6,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyanHighlight,
                                unfocusedBorderColor = StudioBorder,
                                focusedContainerColor = StudioBgDeep,
                                unfocusedContainerColor = StudioBgDeep
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("optional_lyrics_input")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Model Selector Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "STUDIO ENGINE:",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GeminiModelOption.entries.forEach { option ->
                            FilterChip(
                                selected = uiState.selectedModel == option,
                                onClick = { onSelectModel(option) },
                                label = {
                                    Text(
                                        text = option.displayName,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricBlue.copy(alpha = 0.28f),
                                    selectedLabelColor = CyanHighlight
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Primary Analyse Button
                Button(
                    onClick = onRunAnalysis,
                    enabled = !uiState.isAnalyzing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricBlue,
                        contentColor = TextIceWhite
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("run_song_analysis_button")
                ) {
                    if (uiState.isAnalyzing) {
                        CircularProgressIndicator(
                            color = TextIceWhite,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = uiState.analysisStage.title + "...",
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    } else {
                        Text(
                            text = if (uiState.selectedAudioFileName != null) {
                                "Analyse \"${uiState.selectedAudioFileName}\" → Song Sheet"
                            } else {
                                "Analyse Audio → Playable Song Sheet"
                            },
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }

        // Real Multi-Stage Analysis Progress Stepper (when analyzing)
        AnimatedVisibility(visible = uiState.isAnalyzing) {
            AnalysisProgressPipelineCard(currentStage = uiState.analysisStage)
        }

        // What Don't Fret Answers (Quick Spec Capability Summary Pill Row)
        Surface(
            color = StudioSurface,
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "AUDIO IN → ANALYSIS → ONE CLEAN PLAYABLE SONG SHEET",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = CyanHighlight
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Detects concert key, tempo/BPM, time signature, tuning offset, chord timeline, and calculates true Capo & open-chord guitar versions while keeping the original sounding key.",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }
        }

        // Recent & Starter Studio Projects on Home Screen
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RECENT & STUDIO REFERENCE PROJECTS",
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = TextMuted,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = "${recentProjects.size} saved",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 11.sp,
                    color = CyanHighlight
                )
            }

            recentProjects.take(4).forEach { project ->
                Surface(
                    color = StudioSurface,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (project.id == uiState.activeProject.id) ElectricBlue else StudioBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onOpenProject(project) }
                        .testTag("home_recent_project_${project.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = project.title,
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = TextIceWhite
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(
                                            if (project.isSampleProject) DciViolet.copy(alpha = 0.2f)
                                            else ConfidenceEmerald.copy(alpha = 0.2f)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (project.isSampleProject) "SAMPLE" else "ANALYSED",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp,
                                        color = if (project.isSampleProject) CyanHighlight else ConfidenceEmerald
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Key: ${project.detectedKey}  •  ${if (project.recommendedCapo > 0) "Capo ${project.recommendedCapo} (${project.playableKey} shapes)" else "Open ${project.playableKey}"}  •  ${project.tempoBpm} BPM",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.sp,
                                color = ChordAmber
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Chords: ${project.chordsUsed.joinToString(" – ")}",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Open Song Sheet",
                            tint = CyanHighlight
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalysisProgressPipelineCard(currentStage: AnalysisStage) {
    val stages = listOf(
        AnalysisStage.READING_AUDIO,
        AnalysisStage.FINDING_TEMPO,
        AnalysisStage.DETECTING_KEY,
        AnalysisStage.MAPPING_CHORDS,
        AnalysisStage.FINDING_SECTIONS,
        AnalysisStage.BUILDING_GUITAR,
        AnalysisStage.PREPARING_SHEET
    )
    val currentIdx = stages.indexOf(currentStage).coerceAtLeast(0)

    Surface(
        color = StudioBgDeep,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CyanHighlight.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "STUDIO ANALYSIS PIPELINE",
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = CyanHighlight
            )
            stages.forEachIndexed { idx, stage ->
                val isDone = idx < currentIdx
                val isCurrent = idx == currentIdx
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isDone) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Completed",
                            tint = ConfidenceEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                    } else if (isCurrent) {
                        CircularProgressIndicator(
                            color = ChordAmber,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.RadioButtonUnchecked,
                            contentDescription = "Pending",
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = stage.title,
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp,
                            color = when {
                                isCurrent -> ChordAmber
                                isDone -> TextIceWhite
                                else -> TextMuted
                            }
                        )
                        if (isCurrent) {
                            Text(
                                text = stage.detail,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun resolveFileName(context: Context, uri: Uri): String {
    var name: String? = null
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) {
                    name = cursor.getString(idx)
                }
            }
        }
    }
    return name ?: uri.lastPathSegment?.substringAfterLast("/") ?: "uploaded_song.mp3"
}
