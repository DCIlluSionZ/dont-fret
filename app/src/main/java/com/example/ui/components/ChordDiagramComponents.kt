package com.example.ui.components

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.domain.MusicTheoryEngine
import com.example.model.ChordVoicing
import com.example.ui.theme.ChordAmber
import com.example.ui.theme.ChordGoldDeep
import com.example.ui.theme.CyanHighlight
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

/**
 * Interactive compact Fretboard Diagram Card used in the Song Sheet's Chords Grid.
 */
@Composable
fun GuitarChordCard(
    chordName: String,
    isHighlighted: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val voicings = remember(chordName) { MusicTheoryEngine.getVoicingsForChord(chordName) }
    val primary = voicings.first()

    val borderColor = if (isHighlighted) CyanHighlight else StudioBorder
    val bgColor = if (isHighlighted) StudioSurfaceElevated else StudioBgDeep

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(if (isHighlighted) 1.5.dp else 1.dp, borderColor),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .testTag("chord_card_$chordName")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = chordName,
                fontFamily = SpaceGroteskFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = if (isHighlighted) CyanHighlight else ChordAmber
            )
            Spacer(modifier = Modifier.height(2.dp))
            GuitarFretboardCanvas(
                voicing = primary,
                width = 88.dp,
                height = 112.dp
            )
            if (voicings.size > 1) {
                Text(
                    text = "${voicings.size} voicings",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 9.sp,
                    color = TextMuted
                )
            }
        }
    }
}

/**
 * High-precision Jetpack Compose Canvas Guitar Fretboard Renderer.
 * Renders 6 strings, 4 frets, thick nut or baseFret indicator, open/muted markers,
 * barre bars, and numbered finger positions (1=Index..4=Pinky).
 */
@Composable
fun GuitarFretboardCanvas(
    voicing: ChordVoicing,
    width: Dp = 110.dp,
    height: Dp = 140.dp,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier.size(width, height)) {
        val w = size.width
        val h = size.height

        val leftPad = w * 0.17f
        val rightPad = w * 0.11f
        val topPad = h * 0.20f
        val bottomPad = h * 0.08f

        val gridW = w - leftPad - rightPad
        val gridH = h - topPad - bottomPad
        val stringSpacing = gridW / 5f
        val fretSpacing = gridH / 4f

        // 1. Draw Nut (thick if baseFret == 1)
        val nutStroke = if (voicing.baseFret == 1) (h * 0.032f) else (h * 0.012f)
        drawLine(
            color = TextIceWhite,
            start = Offset(leftPad, topPad),
            end = Offset(leftPad + gridW, topPad),
            strokeWidth = nutStroke,
            cap = StrokeCap.Round
        )

        // Base fret label (e.g. "3fr") if baseFret > 1
        if (voicing.baseFret > 1) {
            val frLabel = "${voicing.baseFret}fr"
            val result = textMeasurer.measure(
                text = frLabel,
                style = TextStyle(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    color = ChordAmber
                )
            )
            drawText(
                textLayoutResult = result,
                topLeft = Offset(
                    x = (leftPad - result.size.width - 4f).coerceAtLeast(1f),
                    y = topPad + fretSpacing * 0.5f - result.size.height / 2f
                )
            )
        }

        // 2. Draw horizontal fret wires (1..4)
        for (f in 1..4) {
            val y = topPad + f * fretSpacing
            drawLine(
                color = Color(0xFF475569),
                start = Offset(leftPad, y),
                end = Offset(leftPad + gridW, y),
                strokeWidth = h * 0.01f
            )
        }

        // 3. Draw 6 vertical strings (E A D G B e)
        for (s in 0 until 6) {
            val x = leftPad + s * stringSpacing
            val thickness = (h * 0.012f) * (1.25f - s * 0.08f)
            drawLine(
                color = Color(0xFF64748B),
                start = Offset(x, topPad),
                end = Offset(x, topPad + gridH),
                strokeWidth = thickness
            )
        }

        // 4. Detect if Index Finger (1) forms a Barre across multiple strings at fret 1
        val barreStrings = (0 until 6).filter { s ->
            voicing.frets.getOrElse(s) { -1 } == 1 && voicing.fingers.getOrElse(s) { 0 } == 1
        }
        if (barreStrings.size >= 2) {
            val firstS = barreStrings.first()
            val lastS = barreStrings.last()
            val startX = leftPad + firstS * stringSpacing
            val endX = leftPad + lastS * stringSpacing
            val cy = topPad + 0.5f * fretSpacing
            val barHeight = stringSpacing * 0.55f
            drawRoundRect(
                color = ChordGoldDeep.copy(alpha = 0.75f),
                topLeft = Offset(startX - barHeight / 2f, cy - barHeight / 2f),
                size = Size((endX - startX) + barHeight, barHeight),
                cornerRadius = CornerRadius(barHeight / 2f, barHeight / 2f)
            )
        }

        // 5. Draw Open (o), Muted (x), and Fretted Dots + Finger Numbers
        val dotRadius = (stringSpacing * 0.38f).coerceAtMost(fretSpacing * 0.36f)
        for (s in 0 until 6) {
            val x = leftPad + s * stringSpacing
            val fret = voicing.frets.getOrElse(s) { -1 }
            val finger = voicing.fingers.getOrElse(s) { 0 }

            if (fret <= 0) {
                val marker = if (fret == 0) "o" else "x"
                val markerColor = if (fret == 0) CyanHighlight else TextMuted
                val res = textMeasurer.measure(
                    text = marker,
                    style = TextStyle(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = markerColor
                    )
                )
                drawText(
                    textLayoutResult = res,
                    topLeft = Offset(x - res.size.width / 2f, topPad - res.size.height - 4f)
                )
            } else if (fret in 1..4) {
                val cy = topPad + (fret - 0.5f) * fretSpacing
                drawCircle(
                    color = ChordAmber,
                    radius = dotRadius,
                    center = Offset(x, cy)
                )
                if (finger > 0) {
                    val fRes = textMeasurer.measure(
                        text = finger.toString(),
                        style = TextStyle(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            color = StudioBgDeep
                        )
                    )
                    drawText(
                        textLayoutResult = fRes,
                        topLeft = Offset(x - fRes.size.width / 2f, cy - fRes.size.height / 2f)
                    )
                }
            }
        }
    }
}

/**
 * Interactive Chord Help Modal ("Tapping any chord should open a small guitar chord diagram.
 * Show: Chord name, Finger positions, Muted/open strings, Alternative fingering where useful").
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChordHelpModal(
    chordName: String,
    soundingChordNote: String,
    capoFret: Int,
    onStrumPreview: (ChordVoicing) -> Unit,
    onDismiss: () -> Unit
) {
    val voicings = remember(chordName) { MusicTheoryEngine.getVoicingsForChord(chordName) }
    var selectedVoicingIdx by remember(chordName) { mutableIntStateOf(0) }
    val activeVoicing = voicings.getOrElse(selectedVoicingIdx) { voicings.first() }

    val stringNames = listOf("6th (Low E)", "5th (A)", "4th (D)", "3rd (G)", "2nd (B)", "1st (High e)")
    val fingerNames = mapOf(
        1 to "1 • Index Finger",
        2 to "2 • Middle Finger",
        3 to "3 • Ring Finger",
        4 to "4 • Pinky Finger"
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = StudioSurface,
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("chord_help_modal")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = chordName,
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 26.sp,
                                color = ChordAmber
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(StudioSurfaceElevated)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = if (capoFret > 0) "Capo $capoFret → Sounds as $soundingChordNote" else "Concert Pitch ($soundingChordNote)",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 11.sp,
                                    color = CyanHighlight
                                )
                            }
                        }
                        Text(
                            text = activeVoicing.variationName,
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_chord_modal_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Chord Diagram",
                            tint = TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Alternative Voicing Selector Chips
                if (voicings.size > 1) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        voicings.forEachIndexed { idx, v ->
                            FilterChip(
                                selected = idx == selectedVoicingIdx,
                                onClick = { selectedVoicingIdx = idx },
                                label = {
                                    Text(
                                        text = v.variationName,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricBlue.copy(alpha = 0.25f),
                                    selectedLabelColor = CyanHighlight
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Large Fretboard + String-by-String Breakdown Side by Side
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(StudioBgDeep)
                        .border(1.dp, StudioBorder, RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    GuitarFretboardCanvas(
                        voicing = activeVoicing,
                        width = 135.dp,
                        height = 172.dp
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = "STRING & FINGER GUIDE",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = CyanHighlight
                        )
                        for (s in 0 until 6) {
                            val relFret = activeVoicing.frets.getOrElse(s) { -1 }
                            val finger = activeVoicing.fingers.getOrElse(s) { 0 }
                            val actualFret = if (relFret > 0) (activeVoicing.baseFret - 1) + relFret else relFret
                            val statusText = when {
                                relFret < 0 -> "Muted (X)"
                                relFret == 0 -> "Open (0)"
                                else -> "Fret $actualFret • ${fingerNames[finger] ?: "Fret"}"
                            }
                            val statusColor = when {
                                relFret < 0 -> TextMuted
                                relFret == 0 -> CyanHighlight
                                else -> TextIceWhite
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringNames[s],
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 10.sp,
                                    color = TextMuted
                                )
                                Text(
                                    text = statusText,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 10.sp,
                                    color = statusColor
                                )
                            }
                        }
                    }
                }

                if (activeVoicing.playingTip.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "💡 ${activeVoicing.playingTip}",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Strum Preview & Done Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onStrumPreview(activeVoicing) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ChordAmber,
                            contentColor = StudioBgDeep
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("strum_chord_preview_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Strum Chord Audio",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Strum $chordName",
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
