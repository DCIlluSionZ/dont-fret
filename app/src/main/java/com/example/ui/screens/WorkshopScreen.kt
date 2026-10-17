package com.example.ui.screens

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.LineDiagnostic
import com.example.model.LineRewriteSuggestion
import com.example.model.LyricDiagnosisReport
import com.example.model.SunoStyleAnalysis
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
import com.example.ui.theme.WarningCoral
import com.example.util.SheetExportManager
import com.example.viewmodel.DontFretUiState

private val REWRITE_MODES = listOf(
    "Keep cadence",
    "Humanise",
    "Tighten",
    "Alternative line",
    "Improve rhyme",
    "Keep meaning",
    "More conversational",
    "More Australian",
    "More country",
    "More rock",
    "Less cliché"
)

@Composable
fun WorkshopScreen(
    uiState: DontFretUiState,
    onSelectSubTab: (Int) -> Unit,
    onPopulateFromTrack: () -> Unit,
    onUpdateLyricsDraft: (String) -> Unit,
    onRunLyricDiagnosis: () -> Unit,
    onSelectLineForRewrite: (LineDiagnostic) -> Unit,
    onToggleKeepCadence: () -> Unit,
    onSelectRewriteMode: (String) -> Unit,
    onApplyLineSuggestion: (LineRewriteSuggestion) -> Unit,
    onUpdateStylePromptDraft: (String) -> Unit,
    onRunStyleAnalysis: () -> Unit,
    onApplyRecommendedStylePrompt: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Workshop Header & Sub-Tab Switcher
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
                    Column {
                        Text(
                            text = "SUNO 6 WORKSHOP",
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = TextIceWhite
                        )
                        Text(
                            text = "Lyric diagnosis, cadence-locked rewrites & Suno v6 style precision",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(StudioSurfaceElevated)
                            .border(1.dp, CyanHighlight.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .clickable { onPopulateFromTrack() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("workshop_populate_from_track_button")
                    ) {
                        Text(
                            text = "Load Active Song",
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = CyanHighlight
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Sub-tabs: 0 = Lyric Diagnosis & Cadence Editor, 1 = Suno 6 Style Prompt Analyzer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(StudioBgDeep)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    WorkshopTabButton(
                        title = "1. Lyric Diagnosis & Cadence",
                        selected = uiState.workshopSubTab == 0,
                        onClick = { onSelectSubTab(0) },
                        modifier = Modifier.weight(1f),
                        testTag = "workshop_subtab_lyrics"
                    )
                    WorkshopTabButton(
                        title = "2. Suno 6 Style Prompt",
                        selected = uiState.workshopSubTab == 1,
                        onClick = { onSelectSubTab(1) },
                        modifier = Modifier.weight(1f),
                        testTag = "workshop_subtab_style"
                    )
                }
            }
        }

        if (uiState.workshopSubTab == 0) {
            LyricDiagnosisSection(
                lyricsDraft = uiState.workshopLyricsDraft,
                diagnosis = uiState.lyricDiagnosis,
                selectedLine = uiState.selectedDiagnosticLine,
                keepCadence = uiState.keepCadenceLock,
                selectedRewriteMode = uiState.selectedRewriteMode,
                suggestions = uiState.lineRewriteSuggestions,
                isRewriting = uiState.isRewritingLine,
                onUpdateDraft = onUpdateLyricsDraft,
                onRunDiagnosis = onRunLyricDiagnosis,
                onSelectLine = onSelectLineForRewrite,
                onToggleKeepCadence = onToggleKeepCadence,
                onSelectMode = onSelectRewriteMode,
                onApplySuggestion = onApplyLineSuggestion,
                onCopyLyrics = {
                    SheetExportManager.copyToClipboard(context, "Workshop Lyrics", uiState.workshopLyricsDraft)
                }
            )
        } else {
            SunoStyleAnalyzerSection(
                styleDraft = uiState.workshopStylePromptDraft,
                analysis = uiState.sunoStyleAnalysis,
                isAnalyzing = uiState.isAnalyzingStyle,
                onUpdateStyleDraft = onUpdateStylePromptDraft,
                onRunAnalysis = onRunStyleAnalysis,
                onApplyRecommended = onApplyRecommendedStylePrompt,
                onCopyPrompt = { text ->
                    SheetExportManager.copyToClipboard(context, "Suno 6 Style Prompt", text)
                }
            )
        }
    }
}

@Composable
private fun WorkshopTabButton(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) ElectricBlue else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 9.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            fontFamily = SpaceGroteskFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = if (selected) TextIceWhite else TextMuted
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LyricDiagnosisSection(
    lyricsDraft: String,
    diagnosis: LyricDiagnosisReport?,
    selectedLine: LineDiagnostic?,
    keepCadence: Boolean,
    selectedRewriteMode: String,
    suggestions: List<LineRewriteSuggestion>,
    isRewriting: Boolean,
    onUpdateDraft: (String) -> Unit,
    onRunDiagnosis: () -> Unit,
    onSelectLine: (LineDiagnostic) -> Unit,
    onToggleKeepCadence: () -> Unit,
    onSelectMode: (String) -> Unit,
    onApplySuggestion: (LineRewriteSuggestion) -> Unit,
    onCopyLyrics: () -> Unit
) {
    // 1. Editable Lyrics Input Card
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
                    text = "WORKING LYRIC DRAFT (ANALYSIS FIRST — NO AUTO-OVERWRITE)",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = CyanHighlight
                )
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(StudioBgDeep)
                        .clickable { onCopyLyrics() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Draft",
                        tint = ChordAmber,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "Copy",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 10.sp,
                        color = ChordAmber
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = lyricsDraft,
                onValueChange = onUpdateDraft,
                minLines = 5,
                maxLines = 10,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyanHighlight,
                    unfocusedBorderColor = StudioBorder,
                    focusedContainerColor = StudioBgDeep,
                    unfocusedContainerColor = StudioBgDeep
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("workshop_lyrics_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onRunDiagnosis,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricBlue,
                    contentColor = TextIceWhite
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("run_lyric_diagnosis_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Diagnose Lyrics",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Diagnose Structure, Cadence, Rhyme & AI Clichés",
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }

    // 2. Comprehensive 13-Point Diagnosis Summary Card
    if (diagnosis != null) {
        Surface(
            color = StudioSurface,
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DIAGNOSIS REPORT",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = CyanHighlight
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ConfidenceEmerald.copy(alpha = 0.18f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "Cadence Consistency: ${diagnosis.cadenceConsistencyScore}%",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = ConfidenceEmerald
                        )
                    }
                }

                DiagnosticMetricRow("Structure & Length", "${diagnosis.lyricStructureSummary} ${diagnosis.sectionLengthNotes}")
                DiagnosticMetricRow("Rhyme & Hook", "${diagnosis.rhymePatternSummary} ${diagnosis.hookStrength}")
                DiagnosticMetricRow("Verse vs Chorus Lift", diagnosis.verseChorusDifferentiation)
                DiagnosticMetricRow("POV & Tense", "${diagnosis.povConsistency} • ${diagnosis.tenseConsistency}")
                DiagnosticMetricRow("Emotional Arc & Style", "${diagnosis.emotionalProgression} (${diagnosis.genreStyleCoherence})")

                if (diagnosis.detectedCliches.isNotEmpty()) {
                    DiagnosticMetricRow(
                        label = "Flagged AI Clichés",
                        value = diagnosis.detectedCliches.joinToString(", ") { "\"$it\"" },
                        accent = WarningCoral
                    )
                } else {
                    DiagnosticMetricRow(
                        label = "Cliché Check",
                        value = "Zero obvious AI clichés detected — grounded imagery",
                        accent = ConfidenceEmerald
                    )
                }
            }
        }

        // 3. Interactive Line-by-Line Cadence Selector & Rewriter
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
                Text(
                    text = "TAP ANY LINE TO REWRITE OR HUMANISE WITH CADENCE LOCK",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.5.sp,
                    color = ChordAmber
                )

                diagnosis.lineDiagnostics.take(10).forEach { lineDiag ->
                    val isSel = selectedLine?.lineIndex == lineDiag.lineIndex
                    val hasWarning = lineDiag.flags.any { it.contains("Cliché") || it.contains("Crowded") }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSel) StudioSurfaceElevated else StudioBgDeep)
                            .border(
                                1.dp,
                                when {
                                    isSel -> CyanHighlight
                                    hasWarning -> WarningCoral.copy(alpha = 0.7f)
                                    else -> StudioBorder
                                },
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { onSelectLine(lineDiag) }
                            .padding(10.dp)
                            .testTag("diagnostic_line_${lineDiag.lineIndex}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = lineDiag.sectionLabel,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 10.sp,
                                    color = CyanHighlight
                                )
                                Text(
                                    text = "• ${lineDiag.syllableCount} syllables • Rhyme: -${lineDiag.rhymeEnding}",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 10.sp,
                                    color = ChordAmber
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = lineDiag.text,
                                fontSize = 13.5.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                color = TextIceWhite
                            )
                            if (lineDiag.flags.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = lineDiag.flags.joinToString(" • "),
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 10.sp,
                                    color = if (hasWarning) WarningCoral else ConfidenceEmerald
                                )
                            }
                        }
                    }
                }

                // Selected Line Cadence-Preserving Rewriter Panel
                if (selectedLine != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(StudioBgDeep)
                            .border(1.dp, CyanHighlight.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "SELECTED LINE CADENCE FOOTPRINT",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    color = CyanHighlight
                                )
                                Text(
                                    text = "${selectedLine.syllableCount} Syllables  •  Meter: ${selectedLine.stressPattern}",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp,
                                    color = ChordAmber
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Keep Cadence Lock",
                                    tint = if (keepCadence) ConfidenceEmerald else TextMuted,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Keep Cadence",
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = if (keepCadence) ConfidenceEmerald else TextMuted
                                )
                                Switch(
                                    checked = keepCadence,
                                    onCheckedChange = { onToggleKeepCadence() },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = TextIceWhite,
                                        checkedTrackColor = ConfidenceEmerald
                                    ),
                                    modifier = Modifier.testTag("keep_cadence_switch")
                                )
                            }
                        }

                        // 11 Transformation Action Chips
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            REWRITE_MODES.forEach { mode ->
                                val isSelected = selectedRewriteMode.equals(mode, ignoreCase = true)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onSelectMode(mode) },
                                    label = {
                                        Text(
                                            text = mode,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ElectricBlue.copy(alpha = 0.3f),
                                        selectedLabelColor = CyanHighlight
                                    )
                                )
                            }
                        }

                        if (isRewriting) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = CyanHighlight,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Crafting cadence-matched alternatives ($selectedRewriteMode)...",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        } else if (suggestions.isNotEmpty()) {
                            suggestions.forEachIndexed { idx, sug ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(StudioSurface)
                                        .border(1.dp, StudioBorder, RoundedCornerShape(10.dp))
                                        .clickable { onApplySuggestion(sug) }
                                        .padding(10.dp)
                                        .testTag("apply_rewrite_suggestion_$idx"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "\"${sug.text}\"",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.5.sp,
                                            color = TextIceWhite
                                        )
                                        Text(
                                            text = "${sug.syllableCount} syllables • ${sug.stressPattern} • ${sug.rationale}",
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontSize = 10.sp,
                                            color = ChordAmber
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(ConfidenceEmerald.copy(alpha = 0.2f))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "Apply",
                                            fontFamily = SpaceGroteskFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = ConfidenceEmerald
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
private fun SunoStyleAnalyzerSection(
    styleDraft: String,
    analysis: SunoStyleAnalysis?,
    isAnalyzing: Boolean,
    onUpdateStyleDraft: (String) -> Unit,
    onRunAnalysis: () -> Unit,
    onApplyRecommended: (String) -> Unit,
    onCopyPrompt: (String) -> Unit
) {
    Surface(
        color = StudioSurface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "SUNO 6 STYLE PROMPT ANALYSER",
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = CyanHighlight
            )
            Text(
                text = "Avoid giant keyword soup. Build concise, musically meaningful Suno v6 style prompts with clear instrumentation, vocal character, dynamics, and arrangement.",
                fontSize = 12.sp,
                color = TextSecondary
            )

            OutlinedTextField(
                value = styleDraft,
                onValueChange = onUpdateStyleDraft,
                label = { Text("Your Suno 6 Style Prompt") },
                placeholder = { Text("e.g. Heartland Australian pub-rock, 118 BPM, Maton acoustic + crunchy Telecaster, raw baritone vocal...") },
                minLines = 3,
                maxLines = 5,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyanHighlight,
                    unfocusedBorderColor = StudioBorder,
                    focusedContainerColor = StudioBgDeep,
                    unfocusedContainerColor = StudioBgDeep
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("workshop_style_prompt_input")
            )

            Button(
                onClick = onRunAnalysis,
                enabled = !isAnalyzing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricBlue,
                    contentColor = TextIceWhite
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("run_style_analysis_button")
            ) {
                if (isAnalyzing) {
                    CircularProgressIndicator(
                        color = TextIceWhite,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = "Analyse Style & Build Concise Suno 6 Prompt",
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }

    if (analysis != null) {
        Surface(
            color = StudioSurface,
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "10-POINT MUSICAL STYLE BREAKDOWN",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = ChordAmber
                )
                DiagnosticMetricRow("Genre / Subgenre", "${analysis.genre} • ${analysis.subgenre}")
                DiagnosticMetricRow("Era & Aesthetic", analysis.era)
                DiagnosticMetricRow("Instrumentation", analysis.instrumentation)
                DiagnosticMetricRow("Vocal Character", analysis.vocalCharacter)
                DiagnosticMetricRow("Tempo & Groove", analysis.tempoFeel)
                DiagnosticMetricRow("Dynamics", analysis.dynamics)
                DiagnosticMetricRow("Production Character", analysis.productionCharacter)
                DiagnosticMetricRow("Arrangement", analysis.arrangement)
                DiagnosticMetricRow("Structure Tags", analysis.songStructureTagSummary)

                Spacer(modifier = Modifier.height(6.dp))

                // Editable / Copyable Recommended Concise Suno 6 Prompt
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(StudioBgDeep)
                        .border(1.dp, CyanHighlight.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "CONCISE RECOMMENDED SUNO 6 PROMPT",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = CyanHighlight
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = analysis.conciseRecommendedPrompt,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 12.sp,
                        color = TextIceWhite
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onCopyPrompt(analysis.conciseRecommendedPrompt) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StudioSurfaceElevated,
                                contentColor = ChordAmber
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Copy Prompt",
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        Button(
                            onClick = { onApplyRecommended(analysis.conciseRecommendedPrompt) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricBlue,
                                contentColor = TextIceWhite
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Use in My Draft",
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticMetricRow(
    label: String,
    value: String,
    accent: Color = TextIceWhite
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(StudioBgDeep)
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Text(
            text = label.uppercase(),
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 9.5.sp,
            color = TextMuted
        )
        Text(
            text = value,
            fontSize = 12.5.sp,
            color = accent
        )
    }
}
