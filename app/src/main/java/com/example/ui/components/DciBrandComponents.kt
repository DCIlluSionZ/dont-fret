package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ChordAmber
import com.example.ui.theme.ConfidenceEmerald
import com.example.ui.theme.CyanHighlight
import com.example.ui.theme.DciIceBlue
import com.example.ui.theme.DciSphereGold
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

/**
 * Colorful 3-letter "DCI" wordmark using the existing Space Grotesk Bold font,
 * coloring each letter distinctly:
 * - 'D' in Electric Blue (#38BDF8)
 * - 'C' in Royal Purple / Violet (#A855F7)
 * - 'I' in Warm Gold (#FBBF24)
 */
@Composable
fun ColorfulDciWordmark(
    fontSize: TextUnit = 22.sp,
    letterSpacing: TextUnit = 3.sp,
    modifier: Modifier = Modifier
) {
    Text(
        text = buildAnnotatedString {
            withStyle(
                SpanStyle(
                    color = Color(0xFF38BDF8), // Electric Blue 'D'
                    fontWeight = FontWeight.Bold
                )
            ) {
                append("D")
            }
            withStyle(
                SpanStyle(
                    color = Color(0xFFA855F7), // Royal Purple 'C'
                    fontWeight = FontWeight.Bold
                )
            ) {
                append("C")
            }
            withStyle(
                SpanStyle(
                    color = DciSphereGold, // Warm Gold 'I'
                    fontWeight = FontWeight.Bold
                )
            ) {
                append("I")
            }
        },
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = fontSize,
        letterSpacing = letterSpacing,
        modifier = modifier
    )
}

/**
 * Pure music & guitar-analysis app emblem (guitar pick + fretboard chord dots + audio soundwaves).
 * Contains NO "DCI" text or monogram—purely represents what the app does:
 * Audio In -> Guitar Chord & Song Sheet Analysis.
 */
@Composable
fun DciLogoMark(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    showSacredGeometry: Boolean = false
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        if (showSacredGeometry) {
            drawCircle(
                color = Color(0xFF38BDF8).copy(alpha = 0.16f),
                radius = w * 0.46f,
                center = Offset(w * 0.5f, h * 0.5f),
                style = Stroke(width = w * 0.02f)
            )
        }

        // Guitar Pick Silhouette
        val pickPath = Path().apply {
            moveTo(w * 0.50f, h * 0.10f)
            cubicTo(w * 0.84f, h * 0.10f, w * 0.92f, h * 0.30f, w * 0.92f, h * 0.50f)
            cubicTo(w * 0.92f, h * 0.72f, w * 0.66f, h * 0.88f, w * 0.50f, h * 0.94f)
            cubicTo(w * 0.34f, h * 0.88f, w * 0.08f, h * 0.72f, w * 0.08f, h * 0.50f)
            cubicTo(w * 0.08f, h * 0.30f, w * 0.16f, h * 0.10f, w * 0.50f, h * 0.10f)
            close()
        }

        drawPath(
            path = pickPath,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF0F1E42), Color(0xFF1E1B4B), Color(0xFF091026)),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            )
        )
        drawPath(
            path = pickPath,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF38BDF8), Color(0xFFA855F7), DciSphereGold),
                start = Offset(w * 0.1f, h * 0.1f),
                end = Offset(w * 0.9f, h * 0.9f)
            ),
            style = Stroke(width = w * 0.055f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Left & Right Audio Waveform Bars
        val barStroke = w * 0.05f
        drawLine(
            color = Color(0xFF38BDF8),
            start = Offset(w * 0.22f, h * 0.38f),
            end = Offset(w * 0.22f, h * 0.58f),
            strokeWidth = barStroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFFA855F7),
            start = Offset(w * 0.78f, h * 0.38f),
            end = Offset(w * 0.78f, h * 0.58f),
            strokeWidth = barStroke,
            cap = StrokeCap.Round
        )

        // Center Guitar Fretboard Nut & Strings
        drawLine(
            color = Color(0xFFF8FAFC),
            start = Offset(w * 0.36f, h * 0.26f),
            end = Offset(w * 0.64f, h * 0.26f),
            strokeWidth = w * 0.065f,
            cap = StrokeCap.Round
        )
        // Fret wires
        for (fretY in listOf(0.42f, 0.58f, 0.74f)) {
            drawLine(
                color = Color(0xFF94A3B8).copy(alpha = 0.65f),
                start = Offset(w * 0.36f, h * fretY),
                end = Offset(w * 0.64f, h * fretY),
                strokeWidth = w * 0.03f,
                cap = StrokeCap.Round
            )
        }
        // Guitar strings
        for (strX in listOf(0.38f, 0.50f, 0.62f)) {
            drawLine(
                color = Color(0xFFCBD5E1).copy(alpha = 0.85f),
                start = Offset(w * strX, h * 0.26f),
                end = Offset(w * strX, h * 0.78f),
                strokeWidth = w * 0.03f,
                cap = StrokeCap.Round
            )
        }

        // Glowing Chord Finger Dots (Blue, Purple, Gold)
        drawCircle(
            color = DciSphereGold,
            radius = w * 0.07f,
            center = Offset(w * 0.50f, h * 0.34f)
        )
        drawCircle(
            color = Color(0xFF38BDF8),
            radius = w * 0.07f,
            center = Offset(w * 0.38f, h * 0.50f)
        )
        drawCircle(
            color = Color(0xFFA855F7),
            radius = w * 0.07f,
            center = Offset(w * 0.62f, h * 0.66f)
        )
    }
}

/**
 * Top DCI Brand Banner Card:
 * Displays colorful 3-letter "DCI" lettering (Blue 'D', Purple 'C', Gold 'I') in the existing
 * Space Grotesk Bold font directly on top of "DESIGNED & CODED with INTELLIGENCE",
 * with the purple/blue side icon removed as requested.
 */
@Composable
fun DciMetallicEmblemCard(
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = StudioBgDeep,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF060A17),
                            Color(0xFF0E1733),
                            Color(0xFF060A17)
                        )
                    )
                )
                .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ColorfulDciWordmark(
                fontSize = 24.sp,
                letterSpacing = 3.5.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = DciIceBlue, letterSpacing = 1.4.sp)) {
                        append("DESIGNED & CODED ")
                    }
                    withStyle(SpanStyle(color = ChordAmber, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)) {
                        append("with ")
                    }
                    withStyle(SpanStyle(color = DciIceBlue, letterSpacing = 1.4.sp)) {
                        append("INTELLIGENCE")
                    }
                },
                fontFamily = SpaceGroteskFontFamily,
                fontSize = 10.5.sp
            )
        }
    }
}

/**
 * Top Studio Header Bar for DON'T FRET with Music/Guitar App Icon, Active Key Tier Switcher, and Performance Mode button.
 */
@Composable
fun DontFretTopBar(
    activeKeyTier: String,
    isKeyConfigured: Boolean,
    onToggleKeyTier: () -> Unit,
    onEnterPerformanceMode: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        color = StudioBgDeep,
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Brand Title + Music/Guitar App Icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(StudioSurface)
                            .border(1.dp, StudioBorder, RoundedCornerShape(10.dp))
                            .padding(5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        DciLogoMark(size = 28.dp)
                    }
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "DON'T",
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp,
                                color = TextIceWhite,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = "FRET",
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp,
                                color = CyanHighlight,
                                letterSpacing = 0.8.sp
                            )
                        }
                        Text(
                            text = "GUITAR & SONG SHEET STUDIO",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 9.sp,
                            color = DciIceBlue.copy(alpha = 0.85f),
                            letterSpacing = 0.6.sp
                        )
                    }
                }

                // Right Controls: Tier Routing Pill + Optional Performance Mode
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isPaid = activeKeyTier.equals("PAID", ignoreCase = true)
                    val pillBorderColor = when {
                        !isKeyConfigured -> StudioBorder
                        isPaid -> ChordAmber
                        else -> ConfidenceEmerald
                    }
                    val pillDotColor = when {
                        !isKeyConfigured -> TextMuted
                        isPaid -> ChordAmber
                        else -> ConfidenceEmerald
                    }

                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(StudioSurface)
                            .border(1.dp, pillBorderColor, CircleShape)
                            .clickable { onToggleKeyTier() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("toggle_key_tier_button"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(pillDotColor)
                        )
                        Text(
                            text = if (isPaid) "PAID KEY" else "FREE KEY",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = TextIceWhite
                        )
                    }

                    if (onEnterPerformanceMode != null) {
                        IconButton(
                            onClick = onEnterPerformanceMode,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(StudioSurfaceElevated)
                                .border(1.dp, ElectricBlue.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                .testTag("performance_mode_top_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = "Full Screen Performance Mode",
                                tint = CyanHighlight,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                ElectricBlue.copy(alpha = 0.2f),
                                DciViolet.copy(alpha = 0.6f),
                                CyanHighlight.copy(alpha = 0.2f)
                            )
                        )
                    )
            )
        }
    }
}

/**
 * Persistent subtle DCI lockup banner at the bottom of the app UI:
 * Shows colorful 3-letter "DCI" (Blue, Purple, Gold) + "DESIGNED & CODED with INTELLIGENCE",
 * with the purple/blue icon removed as requested by the user's screenshot.
 */
@Composable
fun DciSignatureFooter(
    modifier: Modifier = Modifier,
    onClickBrandInfo: (() -> Unit)? = null
) {
    Surface(
        color = Color(0xFF060A16),
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClickBrandInfo != null) {
                    Modifier.clickable { onClickBrandInfo() }
                } else {
                    Modifier
                }
            )
            .testTag("dci_signature_footer")
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(StudioBorder.copy(alpha = 0.5f))
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                ColorfulDciWordmark(
                    fontSize = 12.sp,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "•",
                    color = StudioBorder,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = DciIceBlue.copy(alpha = 0.85f), letterSpacing = 1.sp)) {
                            append("DESIGNED & CODED ")
                        }
                        withStyle(SpanStyle(color = ChordAmber, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)) {
                            append("with ")
                        }
                        withStyle(SpanStyle(color = DciIceBlue.copy(alpha = 0.85f), letterSpacing = 1.sp)) {
                            append("INTELLIGENCE")
                        }
                    },
                    fontFamily = SpaceGroteskFontFamily,
                    fontSize = 9.5.sp
                )
            }
        }
    }
}
