package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.ExoPlayerVisualizerFrame
import com.example.domain.MusicTheoryEngine
import com.example.model.ChordEvent
import com.example.model.GuitarDifficulty
import com.example.model.GuitarOption
import com.example.model.SongSection
import com.example.ui.components.ChordHelpModal
import com.example.ui.components.GuitarChordCard
import com.example.ui.theme.ChordAmber
import com.example.ui.theme.ConfidenceEmerald
import com.example.ui.theme.CyanHighlight
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
import kotlin.math.abs

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SongSheetScreen(
    uiState: DontFretUiState,
    activeGuitarOption: GuitarOption,
    onSelectDifficulty: (GuitarDifficulty) -> Unit,
    onSelectGuitarOption: (String) -> Unit,
    onTransposeDelta: (Int) -> Unit,
    onResetTranspose: () -> Unit,
    onAdjustFontSize: (Float) -> Unit,
    onToggleChordsOnly: () -> Unit,
    onToggleSectionCollapse: (String) -> Unit,
    onToggleAutoScroll: () -> Unit,
    onTogglePerformanceMode: (Boolean) -> Unit,
    onTogglePlayback: () -> Unit,
    onSeekToSeconds: (Float) -> Unit,
    onSetPlaybackSpeed: (Float) -> Unit,
    onToggleSectionLoop: (Float, Float) -> Unit,
    onClearSectionLoop: () -> Unit,
    onOpenChordHelp: (String, String) -> Unit,
    onCloseChordHelp: () -> Unit,
    onStrumVoicing: (com.example.model.ChordVoicing) -> Unit,
    onExportPdf: () -> Unit,
    onCopyMarkdown: () -> Unit,
    onCopyPlainText: () -> Unit,
    modifier: Modifier = Modifier
) {
    val project = uiState.activeProject
    val scrollState = rememberScrollState()
    val effectiveDurationSec = uiState.visualizerFrame.durationSec.coerceAtLeast(project.durationSec.toFloat()).coerceAtLeast(1f)

    // ExoPlayer-synchronized proportional Auto-Scroll during playback
    LaunchedEffect(
        uiState.isPlayingAudio,
        uiState.autoScrollEnabled,
        uiState.playbackPositionSec,
        scrollState.maxValue
    ) {
        if (uiState.isPlayingAudio && uiState.autoScrollEnabled && scrollState.maxValue > 0) {
            val fraction = (uiState.playbackPositionSec / effectiveDurationSec).coerceIn(0f, 1f)
            val targetPx = (scrollState.maxValue * fraction).toInt()
            if (abs(targetPx - scrollState.value) > 18) {
                scrollState.animateScrollTo(targetPx)
            }
        }
    }

    // Active & Upcoming Chord Event resolution synced to ExoPlayer position
    val activeChordEventIdx = remember(uiState.playbackPositionSec, project.chordEvents) {
        project.chordEvents.indexOfFirst {
            uiState.playbackPositionSec >= it.startTimeSec && uiState.playbackPositionSec <= it.endTimeSec
        }
    }

    val activeChordEvent: ChordEvent? = remember(activeChordEventIdx, project.chordEvents) {
        if (activeChordEventIdx >= 0) project.chordEvents.getOrNull(activeChordEventIdx) else null
    }

    val nextChordEvent: ChordEvent? = remember(activeChordEventIdx, project.chordEvents) {
        if (activeChordEventIdx >= 0) project.chordEvents.getOrNull(activeChordEventIdx + 1)
        else project.chordEvents.firstOrNull { it.startTimeSec > uiState.playbackPositionSec }
    }

    fun mapSoundingToPlayed(rawChord: String): String {
        val shifted = MusicTheoryEngine.transposeChord(rawChord, uiState.transposeSemitones)
        return activeGuitarOption.chordMapping[shifted]
            ?: MusicTheoryEngine.transposeChord(shifted, -activeGuitarOption.capoFret)
    }

    val activePlayedChord = remember(activeChordEvent, uiState.transposeSemitones, activeGuitarOption) {
        activeChordEvent?.chord?.let { mapSoundingToPlayed(it) }
    }

    val nextPlayedChord = remember(nextChordEvent, uiState.transposeSemitones, activeGuitarOption) {
        nextChordEvent?.chord?.let { mapSoundingToPlayed(it) }
    }

    val modalChordPair = uiState.activeChordHelpModal
    if (modalChordPair != null) {
        ChordHelpModal(
            chordName = modalChordPair.first,
            soundingChordNote = modalChordPair.second,
            capoFret = activeGuitarOption.capoFret,
            onStrumPreview = onStrumVoicing,
            onDismiss = onCloseChordHelp
        )
    }

    if (uiState.isPerformanceMode) {
        BackHandler { onTogglePerformanceMode(false) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (uiState.isPerformanceMode) {
            PerformanceModeTopStrip(
                title = project.title,
                activeOption = activeGuitarOption,
                tempoBpm = project.tempoBpm,
                isPlaying = uiState.isPlayingAudio,
                autoScroll = uiState.autoScrollEnabled,
                activePlayedChord = activePlayedChord,
                nextPlayedChord = nextPlayedChord,
                visualizerFrame = uiState.visualizerFrame,
                onTogglePlay = onTogglePlayback,
                onToggleAutoScroll = onToggleAutoScroll,
                onAdjustFont = onAdjustFontSize,
                onExitPerformanceMode = { onTogglePerformanceMode(false) }
            )
        } else {
            // 1. Song Metadata Header Card
            SongSheetMetadataHeader(
                project = project,
                activeOption = activeGuitarOption
            )

            // 2. Jetpack Media3 ExoPlayer Visualizer, Chord Conveyor & Practice Speed Deck
            ExoPlayerSheetVisualizerCard(
                frame = uiState.visualizerFrame,
                sections = project.sections,
                activePlayedChord = activePlayedChord,
                nextPlayedChord = nextPlayedChord,
                nextChordCountdownSec = nextChordEvent?.let {
                    (it.startTimeSec - uiState.playbackPositionSec).coerceAtLeast(0f)
                },
                autoScrollEnabled = uiState.autoScrollEnabled,
                hasLocalAudioFile = !project.audioUriOrPath.isNullOrBlank(),
                onTogglePlay = onTogglePlayback,
                onSeek = onSeekToSeconds,
                onSetSpeed = onSetPlaybackSpeed,
                onToggleAutoScroll = onToggleAutoScroll,
                onClearLoop = onClearSectionLoop
            )

            // 3. Guitar Mode & Capo Equivalence Selector Card
            GuitarModeCapoCard(
                originalRecordingKey = project.detectedKey,
                transposeSemitones = uiState.transposeSemitones,
                selectedDifficulty = uiState.guitarDifficulty,
                options = uiState.guitarOptions,
                activeOption = activeGuitarOption,
                onSelectDifficulty = onSelectDifficulty,
                onSelectOption = onSelectGuitarOption,
                onTransposeDelta = onTransposeDelta,
                onResetTranspose = onResetTranspose
            )

            // 4. Interactive Fretboard Chord Diagrams Strip
            Surface(
                color = StudioSurface,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CHORD DIAGRAMS (TAP ANY CHORD FOR FINGERING & AUDIO)",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = CyanHighlight
                        )
                        Text(
                            text = if (activeGuitarOption.capoFret > 0) "Capo ${activeGuitarOption.capoFret}" else "Open Nut",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = ChordAmber
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        activeGuitarOption.playedShapes.forEach { playedShape ->
                            val soundingEquivalent = MusicTheoryEngine.transposeChord(
                                playedShape,
                                activeGuitarOption.capoFret
                            )
                            GuitarChordCard(
                                chordName = playedShape,
                                isHighlighted = activePlayedChord == playedShape,
                                onClick = { onOpenChordHelp(playedShape, soundingEquivalent) }
                            )
                        }
                    }
                }
            }

            // 5. Sheet Display Toolbar (Stage Mode, Font Size, Chords Only, Export)
            SheetToolbarRow(
                chordsOnly = uiState.chordsOnlyView,
                onAdjustFontSize = onAdjustFontSize,
                onToggleChordsOnly = onToggleChordsOnly,
                onEnterPerformanceMode = { onTogglePerformanceMode(true) },
                onExportPdf = onExportPdf,
                onCopyMarkdown = onCopyMarkdown,
                onCopyPlainText = onCopyPlainText
            )
        }

        // 6. THE PLAYABLE SONG SHEET SECTIONS
        Surface(
            color = Color(0xFF0E162B),
            shape = RoundedCornerShape(18.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("playable_song_sheet_container")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                project.sections.forEach { section ->
                    val isCurrentSection = uiState.playbackPositionSec >= section.startTimeSec &&
                        uiState.playbackPositionSec < section.endTimeSec &&
                        (uiState.isPlayingAudio || uiState.playbackPositionSec > 0.5f)
                    val isCollapsed = section.label in uiState.collapsedSectionLabels
                    val loopRange = uiState.visualizerFrame.loopRangeSec
                    val isSectionLooping = loopRange != null &&
                        abs(loopRange.first - section.startTimeSec) < 1f &&
                        abs(loopRange.second - section.endTimeSec) < 1f

                    PlayableSectionBlock(
                        section = section,
                        isCurrentSection = isCurrentSection,
                        isSectionLooping = isSectionLooping,
                        isCollapsed = isCollapsed,
                        chordsOnlyView = uiState.chordsOnlyView,
                        fontSizeSp = uiState.sheetFontSizeSp,
                        transposeSemitones = uiState.transposeSemitones,
                        activeGuitarOption = activeGuitarOption,
                        activePlayedChord = activePlayedChord,
                        onSeekToSection = { onSeekToSeconds(section.startTimeSec.toFloat()) },
                        onToggleSectionLoop = {
                            onToggleSectionLoop(section.startTimeSec.toFloat(), section.endTimeSec.toFloat())
                        },
                        onToggleCollapse = { onToggleSectionCollapse(section.label) },
                        onTapInlineChord = onOpenChordHelp
                    )
                }

                if (project.riffTab.isNotBlank() && !uiState.isPerformanceMode) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(StudioBgDeep)
                            .border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "🎸 SIGNATURE RIFF / WALKDOWN TAB",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = ChordAmber
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = project.riffTab,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp,
                            color = TextIceWhite,
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        )
                    }
                }

                if (project.producerNotes.isNotBlank() && !uiState.isPerformanceMode) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(StudioSurfaceElevated.copy(alpha = 0.55f))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "💡 STUDIO GUITAR & ARRANGEMENT NOTES",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = CyanHighlight
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = project.producerNotes,
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Jetpack Media3 ExoPlayer Audio Deck with Interactive Waveform Scrubber,
 * 24-Band Live Spectrum Visualizer, Upcoming Chord Conveyor, Practice Speed (0.5x–1.25x),
 * and Auto-Scroll synchronization.
 */
@Composable
private fun ExoPlayerSheetVisualizerCard(
    frame: ExoPlayerVisualizerFrame,
    sections: List<SongSection>,
    activePlayedChord: String?,
    nextPlayedChord: String?,
    nextChordCountdownSec: Float?,
    autoScrollEnabled: Boolean,
    hasLocalAudioFile: Boolean,
    onTogglePlay: () -> Unit,
    onSeek: (Float) -> Unit,
    onSetSpeed: (Float) -> Unit,
    onToggleAutoScroll: () -> Unit,
    onClearLoop: () -> Unit
) {
    val durationSec = frame.durationSec.coerceAtLeast(1f)
    val progress = (frame.positionSec / durationSec).coerceIn(0f, 1f)

    Surface(
        color = StudioSurface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("exoplayer_visualizer_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Row: Play/Pause, Timestamp, Engine Badge, and Auto-Scroll Chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IconButton(
                        onClick = onTogglePlay,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(ElectricBlue, DciViolet)
                                )
                            )
                            .testTag("sheet_play_pause_button")
                    ) {
                        Icon(
                            imageVector = if (frame.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (frame.isPlaying) "Pause ExoPlayer" else "Play ExoPlayer",
                            tint = TextIceWhite,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "${formatDuration(frame.positionSec.toInt())} / ${formatDuration(durationSec.toInt())}",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = TextIceWhite
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(ElectricBlue.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (hasLocalAudioFile) "EXOPLAYER • LOCAL FILE" else "EXOPLAYER • STUDIO SYNTH",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 8.5.sp,
                                    color = CyanHighlight
                                )
                            }
                        }
                        Text(
                            text = "Tap waveform or section headers to seek • Syncs auto-scroll",
                            fontSize = 10.5.sp,
                            color = TextMuted
                        )
                    }
                }

                FilterChip(
                    selected = autoScrollEnabled,
                    onClick = onToggleAutoScroll,
                    label = {
                        Text(
                            text = "Auto-Scroll",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ElectricBlue.copy(alpha = 0.28f),
                        selectedLabelColor = CyanHighlight
                    ),
                    modifier = Modifier.testTag("auto_scroll_toggle_chip")
                )
            }

            // Live Chord Sync & Spectrum Visualizer Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(StudioBgDeep)
                    .border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Active + Next Chord Conveyor
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column {
                        Text(
                            text = "NOW PLAYING",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 8.5.sp,
                            color = TextMuted
                        )
                        Text(
                            text = activePlayedChord ?: "—",
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = ChordAmber
                        )
                    }

                    if (nextPlayedChord != null) {
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(StudioSurface)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "UP NEXT ${nextChordCountdownSec?.let { "(${String.format("%.1fs", it)})" } ?: ""}",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 8.5.sp,
                                color = CyanHighlight
                            )
                            Text(
                                text = "→ $nextPlayedChord",
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = TextIceWhite
                            )
                        }
                    }
                }

                // 24-Band Real-Time Spectrum Visualizer Canvas
                Canvas(
                    modifier = Modifier
                        .width(128.dp)
                        .height(36.dp)
                ) {
                    val bins = frame.spectrumBins
                    val count = bins.size.coerceAtLeast(1)
                    val barW = (size.width / (count * 1.45f)).coerceAtLeast(2f)
                    val gap = (size.width - barW * count) / (count - 1).coerceAtLeast(1)

                    for (i in 0 until count) {
                        val mag = bins[i].coerceIn(0.12f, 1.0f)
                        val barH = size.height * mag
                        val x = i * (barW + gap)
                        val y = (size.height - barH) / 2f
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(CyanHighlight, ElectricBlue, DciViolet)
                            ),
                            topLeft = Offset(x, y),
                            size = Size(barW, barH),
                            cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                        )
                    }
                }
            }

            // Interactive 64-Bar Waveform Scrubber Canvas (Tap anywhere to seek ExoPlayer!)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(StudioBgDeep)
                    .border(1.dp, StudioBorder, RoundedCornerShape(10.dp))
                    .pointerInput(durationSec) {
                        detectTapGestures { offset ->
                            val tappedFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                            onSeek(tappedFraction * durationSec)
                        }
                    }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .testTag("exoplayer_waveform_scrubber")
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val peaks = frame.waveformPeaks
                    val count = peaks.size.coerceAtLeast(1)
                    val barW = (size.width / (count * 1.5f)).coerceAtLeast(2f)
                    val gap = (size.width - barW * count) / (count - 1).coerceAtLeast(1)

                    // Draw A-B Loop Highlight Zone if active
                    val loop = frame.loopRangeSec
                    if (loop != null) {
                        val startX = (loop.first / durationSec).coerceIn(0f, 1f) * size.width
                        val endX = (loop.second / durationSec).coerceIn(0f, 1f) * size.width
                        drawRoundRect(
                            color = ChordAmber.copy(alpha = 0.16f),
                            topLeft = Offset(startX, 0f),
                            size = Size((endX - startX).coerceAtLeast(4f), size.height),
                            cornerRadius = CornerRadius(6f, 6f)
                        )
                    }

                    // Draw Section Divider Ticks
                    sections.forEach { sec ->
                        val secX = (sec.startTimeSec.toFloat() / durationSec).coerceIn(0f, 1f) * size.width
                        drawLine(
                            color = StudioBorder.copy(alpha = 0.8f),
                            start = Offset(secX, 0f),
                            end = Offset(secX, size.height),
                            strokeWidth = 1.2f
                        )
                    }

                    // Draw Waveform Bars
                    for (i in 0 until count) {
                        val barFraction = i.toFloat() / count.toFloat()
                        val isPassed = barFraction <= progress
                        val peak = peaks[i].coerceIn(0.15f, 1.0f)
                        val barH = size.height * peak
                        val x = i * (barW + gap)
                        val y = (size.height - barH) / 2f

                        val barColor = when {
                            loop != null &&
                                (barFraction * durationSec) in loop.first..loop.second -> ChordAmber
                            isPassed -> CyanHighlight
                            else -> Color(0xFF334155)
                        }

                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(x, y),
                            size = Size(barW, barH),
                            cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                        )
                    }

                    // Playhead Cursor Line
                    val playheadX = progress * size.width
                    drawLine(
                        color = ChordAmber,
                        start = Offset(playheadX, 0f),
                        end = Offset(playheadX, size.height),
                        strokeWidth = 2.5f
                    )
                }
            }

            // Bottom Row: Practice Tempo Speed Chips (0.5x, 0.75x, 1.0x, 1.25x) + Active Loop Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "SPEED:",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                    listOf(0.5f, 0.75f, 1.0f, 1.25f).forEach { spd ->
                        val isSel = abs(frame.playbackSpeed - spd) < 0.05f
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSel) ElectricBlue else StudioBgDeep)
                                .border(1.dp, if (isSel) CyanHighlight else StudioBorder, RoundedCornerShape(6.dp))
                                .clickable { onSetSpeed(spd) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("exoplayer_speed_${spd}")
                        ) {
                            Text(
                                text = "${spd}x",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.5.sp,
                                color = if (isSel) TextIceWhite else TextSecondary
                            )
                        }
                    }
                }

                if (frame.loopRangeSec != null) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ChordAmber.copy(alpha = 0.2f))
                            .border(1.dp, ChordAmber, RoundedCornerShape(6.dp))
                            .clickable { onClearLoop() }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Repeat,
                            contentDescription = "Clear Loop",
                            tint = ChordAmber,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Loop ${formatDuration(frame.loopRangeSec.first.toInt())}–${formatDuration(frame.loopRangeSec.second.toInt())} ✕",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = ChordAmber
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SongSheetMetadataHeader(
    project: com.example.model.SongAnalysisProject,
    activeOption: GuitarOption
) {
    Surface(
        color = StudioSurface,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = project.title,
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        color = TextIceWhite
                    )
                    Text(
                        text = "${project.artistOrSource}  •  ${formatDuration(project.durationSec)}",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(ConfidenceEmerald.copy(alpha = 0.16f))
                        .border(1.dp, ConfidenceEmerald.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${project.confidence.keyConfidencePercent}% Key Lock",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = ConfidenceEmerald
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetaBadgePill(label = "SOUNDING KEY", value = activeOption.soundingKey, accent = CyanHighlight)
                MetaBadgePill(
                    label = "PLAYED SHAPES",
                    value = if (activeOption.capoFret > 0) {
                        "${activeOption.playedKeyShape} (Capo ${activeOption.capoFret})"
                    } else {
                        "${activeOption.playedKeyShape} (No Capo)"
                    },
                    accent = ChordAmber
                )
                MetaBadgePill(label = "TEMPO", value = "${project.tempoBpm} BPM (${project.timeSignature})", accent = TextIceWhite)
                MetaBadgePill(label = "TUNING", value = project.tuning, accent = TextSecondary)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(StudioBgDeep)
                    .border(1.dp, ChordAmber.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "STRUMMING: ${project.strummingPattern}",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = ChordAmber
                    )
                    Text(
                        text = "Feel: ${project.feel}",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun MetaBadgePill(label: String, value: String, accent: Color) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(StudioBgDeep)
            .border(1.dp, StudioBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 9.sp,
            color = TextMuted
        )
        Text(
            text = value,
            fontFamily = SpaceGroteskFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = accent
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GuitarModeCapoCard(
    originalRecordingKey: String,
    transposeSemitones: Int,
    selectedDifficulty: GuitarDifficulty,
    options: List<GuitarOption>,
    activeOption: GuitarOption,
    onSelectDifficulty: (GuitarDifficulty) -> Unit,
    onSelectOption: (String) -> Unit,
    onTransposeDelta: (Int) -> Unit,
    onResetTranspose: () -> Unit
) {
    Surface(
        color = StudioSurface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "GUITAR MODE & CAPO CALCULATOR",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = CyanHighlight
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GuitarDifficulty.entries.forEach { diff ->
                        val isSel = diff == selectedDifficulty
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSel) ElectricBlue else StudioBgDeep)
                                .border(1.dp, if (isSel) CyanHighlight else StudioBorder, RoundedCornerShape(6.dp))
                                .clickable { onSelectDifficulty(diff) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("difficulty_chip_${diff.name}")
                        ) {
                            Text(
                                text = diff.label,
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = if (isSel) TextIceWhite else TextSecondary
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                options.forEach { opt ->
                    val isSelected = opt.id == activeOption.id
                    Surface(
                        color = if (isSelected) StudioSurfaceElevated else StudioBgDeep,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            if (isSelected) 1.5.dp else 1.dp,
                            if (isSelected) ChordAmber else StudioBorder
                        ),
                        modifier = Modifier
                            .width(215.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectOption(opt.id) }
                            .testTag("guitar_option_${opt.id}")
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = opt.name.uppercase(),
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (isSelected) ChordAmber else TextIceWhite
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(
                                            if (opt.capoFret > 0) CyanHighlight.copy(alpha = 0.18f)
                                            else StudioSurface
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (opt.capoFret > 0) "CAPO ${opt.capoFret}" else "NO CAPO",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        color = if (opt.capoFret > 0) CyanHighlight else TextMuted
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Play: ${opt.playedShapes.joinToString(" – ")}",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp,
                                color = TextIceWhite,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Sounding Key: ${opt.soundingKey}",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 10.sp,
                                color = ConfidenceEmerald
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(StudioBgDeep)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(StudioSurfaceElevated)
                            .border(1.dp, StudioBorder, RoundedCornerShape(8.dp))
                            .clickable { onTransposeDelta(-1) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("transpose_minus_button")
                    ) {
                        Text(
                            text = "-1 Semitone",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = TextIceWhite
                        )
                    }

                    Text(
                        text = if (transposeSemitones == 0) {
                            "0 ST"
                        } else {
                            "${if (transposeSemitones > 0) "+" else ""}$transposeSemitones ST"
                        },
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = ChordAmber,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(StudioSurfaceElevated)
                            .border(1.dp, StudioBorder, RoundedCornerShape(8.dp))
                            .clickable { onTransposeDelta(1) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("transpose_plus_button")
                    ) {
                        Text(
                            text = "+1 Semitone",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = TextIceWhite
                        )
                    }

                    if (transposeSemitones != 0) {
                        IconButton(
                            onClick = onResetTranspose,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset Transpose",
                                tint = CyanHighlight,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Text(
                    text = "Orig: $originalRecordingKey → Sounding: ${activeOption.soundingKey}",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun SheetToolbarRow(
    chordsOnly: Boolean,
    onAdjustFontSize: (Float) -> Unit,
    onToggleChordsOnly: () -> Unit,
    onEnterPerformanceMode: () -> Unit,
    onExportPdf: () -> Unit,
    onCopyMarkdown: () -> Unit,
    onCopyPlainText: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SheetToolButton(
            text = "Stage Mode",
            accentColor = CyanHighlight,
            onClick = onEnterPerformanceMode,
            testTag = "enter_performance_mode_button"
        )
        SheetToolButton(
            text = "A-",
            onClick = { onAdjustFontSize(-1.5f) },
            testTag = "font_decrease_button"
        )
        SheetToolButton(
            text = "A+",
            onClick = { onAdjustFontSize(1.5f) },
            testTag = "font_increase_button"
        )
        SheetToolButton(
            text = if (chordsOnly) "Show Lyrics + Chords" else "Chords Only View",
            accentColor = if (chordsOnly) ChordAmber else TextIceWhite,
            onClick = onToggleChordsOnly,
            testTag = "toggle_chords_only_button"
        )
        SheetToolButton(
            text = "PDF / Print",
            accentColor = ChordAmber,
            onClick = onExportPdf,
            testTag = "export_pdf_button"
        )
        SheetToolButton(
            text = "Copy MD",
            onClick = onCopyMarkdown,
            testTag = "copy_markdown_button"
        )
        SheetToolButton(
            text = "Copy Text",
            onClick = onCopyPlainText,
            testTag = "copy_text_button"
        )
    }
}

@Composable
private fun SheetToolButton(
    text: String,
    accentColor: Color = TextIceWhite,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        color = StudioSurface,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder),
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Text(
            text = text,
            fontFamily = SpaceGroteskFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = accentColor,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayableSectionBlock(
    section: SongSection,
    isCurrentSection: Boolean,
    isSectionLooping: Boolean,
    isCollapsed: Boolean,
    chordsOnlyView: Boolean,
    fontSizeSp: Float,
    transposeSemitones: Int,
    activeGuitarOption: GuitarOption,
    activePlayedChord: String?,
    onSeekToSection: () -> Unit,
    onToggleSectionLoop: () -> Unit,
    onToggleCollapse: () -> Unit,
    onTapInlineChord: (String, String) -> Unit
) {
    val borderColor = when {
        isSectionLooping -> ChordAmber
        isCurrentSection -> CyanHighlight
        else -> StudioBorder.copy(alpha = 0.6f)
    }
    val bgColor = if (isCurrentSection || isSectionLooping) {
        StudioSurfaceElevated.copy(alpha = 0.45f)
    } else {
        StudioBgDeep.copy(alpha = 0.65f)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSeekToSection() }
                .testTag("section_header_${section.label}"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = section.label.uppercase(),
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (isCurrentSection) CyanHighlight else ChordAmber,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = "${formatDuration(section.startTimeSec)} – ${formatDuration(section.endTimeSec)}",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 10.sp,
                    color = TextMuted
                )
                if (isCurrentSection) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(CyanHighlight.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "PLAYING",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            color = CyanHighlight
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Loop Section Button for ExoPlayer A-B practice
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSectionLooping) ChordAmber.copy(alpha = 0.24f) else StudioSurface)
                        .border(
                            1.dp,
                            if (isSectionLooping) ChordAmber else StudioBorder,
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { onToggleSectionLoop() }
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                        .testTag("loop_section_${section.label}")
                ) {
                    Text(
                        text = if (isSectionLooping) "🔁 Looping" else "🔁 Loop",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.5.sp,
                        color = if (isSectionLooping) ChordAmber else TextSecondary
                    )
                }

                IconButton(
                    onClick = onToggleCollapse,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = if (isCollapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                        contentDescription = if (isCollapsed) "Expand Section" else "Collapse Section",
                        tint = TextMuted
                    )
                }
            }
        }

        AnimatedVisibility(visible = !isCollapsed) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                section.lines.forEach { rawLine ->
                    val segments = remember(rawLine, transposeSemitones, activeGuitarOption) {
                        parseBracketLineSegments(
                            rawLine = rawLine,
                            transposeSemitones = transposeSemitones,
                            activeOption = activeGuitarOption
                        )
                    }

                    if (chordsOnlyView) {
                        val lineChords = segments.mapNotNull { it.playedChord }
                        if (lineChords.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                lineChords.forEach { (played, sounding) ->
                                    val isChordActive = activePlayedChord == played && isCurrentSection
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isChordActive) CyanHighlight.copy(alpha = 0.25f) else StudioSurface)
                                            .border(
                                                1.dp,
                                                if (isChordActive) CyanHighlight else ChordAmber.copy(alpha = 0.5f),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable { onTapInlineChord(played, sounding) }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "| $played ",
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = fontSizeSp.sp,
                                            color = if (isChordActive) CyanHighlight else ChordAmber
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start,
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            segments.forEach { seg ->
                                Column(
                                    horizontalAlignment = Alignment.Start,
                                    modifier = Modifier.padding(end = 4.dp, bottom = 4.dp)
                                ) {
                                    val chordPair = seg.playedChord
                                    if (chordPair != null) {
                                        val isActive = activePlayedChord == chordPair.first && isCurrentSection
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(5.dp))
                                                .background(
                                                    if (isActive) CyanHighlight.copy(alpha = 0.25f)
                                                    else ChordAmber.copy(alpha = 0.14f)
                                                )
                                                .clickable {
                                                    onTapInlineChord(chordPair.first, chordPair.second)
                                                }
                                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = chordPair.first,
                                                fontFamily = JetBrainsMonoFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = (fontSizeSp - 2.5f).coerceAtLeast(11f).sp,
                                                color = if (isActive) CyanHighlight else ChordAmber
                                            )
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.height((fontSizeSp + 3f).dp))
                                    }

                                    if (seg.lyricText.isNotEmpty()) {
                                        Text(
                                            text = seg.lyricText,
                                            fontSize = fontSizeSp.sp,
                                            color = TextIceWhite,
                                            lineHeight = (fontSizeSp * 1.35f).sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PerformanceModeTopStrip(
    title: String,
    activeOption: GuitarOption,
    tempoBpm: Int,
    isPlaying: Boolean,
    autoScroll: Boolean,
    activePlayedChord: String?,
    nextPlayedChord: String?,
    visualizerFrame: ExoPlayerVisualizerFrame,
    onTogglePlay: () -> Unit,
    onToggleAutoScroll: () -> Unit,
    onAdjustFont: (Float) -> Unit,
    onExitPerformanceMode: () -> Unit
) {
    Surface(
        color = StudioBgDeep,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, CyanHighlight),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = TextIceWhite,
                        maxLines = 1
                    )
                    Text(
                        text = "${if (activeOption.capoFret > 0) "Capo ${activeOption.capoFret} (${activeOption.playedKeyShape})" else "Open ${activeOption.playedKeyShape}"} • ${activePlayedChord ?: "Ready"} ${nextPlayedChord?.let { "→ $it" } ?: ""}",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = ChordAmber
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = onTogglePlay,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(ElectricBlue)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play or Pause",
                            tint = TextIceWhite
                        )
                    }
                    SheetToolButton(
                        text = "A+",
                        onClick = { onAdjustFont(1.5f) },
                        testTag = "perf_font_plus"
                    )
                    IconButton(
                        onClick = onExitPerformanceMode,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(StudioSurfaceElevated)
                            .testTag("exit_performance_mode_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FullscreenExit,
                            contentDescription = "Exit Stage Mode",
                            tint = CyanHighlight
                        )
                    }
                }
            }
        }
    }
}

private data class ChordLyricSegment(
    val playedChord: Pair<String, String>?,
    val lyricText: String
)

private fun parseBracketLineSegments(
    rawLine: String,
    transposeSemitones: Int,
    activeOption: GuitarOption
): List<ChordLyricSegment> {
    val result = mutableListOf<ChordLyricSegment>()
    val regex = Regex("\\[([^\\]]+)\\]([^\\[]*)")
    val matches = regex.findAll(rawLine).toList()

    if (matches.isEmpty()) {
        return listOf(ChordLyricSegment(null, rawLine))
    }

    val firstMatchStart = matches.first().range.first
    if (firstMatchStart > 0) {
        val leading = rawLine.substring(0, firstMatchStart)
        if (leading.isNotBlank()) {
            result.add(ChordLyricSegment(null, leading))
        }
    }

    for (m in matches) {
        val rawChord = m.groupValues[1].trim()
        val followingText = m.groupValues[2]
        val soundingChord = MusicTheoryEngine.transposeChord(rawChord, transposeSemitones)
        val playedShape = activeOption.chordMapping[soundingChord]
            ?: MusicTheoryEngine.transposeChord(soundingChord, -activeOption.capoFret)

        result.add(
            ChordLyricSegment(
                playedChord = playedShape to soundingChord,
                lyricText = followingText.ifEmpty { "   " }
            )
        )
    }
    return result
}

private fun formatDuration(totalSec: Int): String {
    val m = (totalSec / 60).coerceAtLeast(0)
    val s = (totalSec % 60).coerceAtLeast(0)
    return "%d:%02d".format(m, s)
}
