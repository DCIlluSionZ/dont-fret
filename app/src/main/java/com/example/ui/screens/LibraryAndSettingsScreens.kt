package com.example.ui.screens

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.model.GuitarDifficulty
import com.example.model.SongAnalysisProject
import com.example.network.GeminiModelOption
import com.example.ui.components.DciLogoMark
import com.example.ui.components.DciMetallicEmblemCard
import com.example.ui.theme.ChordAmber
import com.example.ui.theme.ConfidenceEmerald
import com.example.ui.theme.CyanHighlight
import com.example.ui.theme.DangerRose
import com.example.ui.theme.DciIceBlue
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
import com.example.viewmodel.DontFretUiState

@Composable
fun LibraryScreen(
    uiState: DontFretUiState,
    projects: List<SongAnalysisProject>,
    onUpdateSearch: (String) -> Unit,
    onOpenProject: (SongAnalysisProject) -> Unit,
    onDeleteProject: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val filtered = remember(projects, uiState.librarySearchQuery) {
        val q = uiState.librarySearchQuery.trim().lowercase()
        if (q.isEmpty()) {
            projects
        } else {
            projects.filter {
                it.title.lowercase().contains(q) ||
                    it.detectedKey.lowercase().contains(q) ||
                    it.chordsUsed.any { c -> c.lowercase().contains(q) }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            color = StudioSurface,
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "PROJECT LIBRARY",
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = TextIceWhite
                )
                Text(
                    text = "Saved song sheets, capo configurations, lyric diagnoses & audio analyses",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = uiState.librarySearchQuery,
                    onValueChange = onUpdateSearch,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search Library",
                            tint = CyanHighlight
                        )
                    },
                    placeholder = { Text("Search by title, key (e.g. Bb), or chord...") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanHighlight,
                        unfocusedBorderColor = StudioBorder,
                        focusedContainerColor = StudioBgDeep,
                        unfocusedContainerColor = StudioBgDeep
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("library_search_input")
                )
            }
        }

        if (filtered.isEmpty()) {
            Surface(
                color = StudioSurface,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = "Empty Library",
                        tint = CyanHighlight,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No matching song sheets found",
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = TextIceWhite
                    )
                    Text(
                        text = "Drop in an audio track on the Song tab to build a new playable song sheet.",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }
            }
        } else {
            filtered.forEach { project ->
                val isCurrent = project.id == uiState.activeProject.id
                Surface(
                    color = StudioSurface,
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        if (isCurrent) 1.5.dp else 1.dp,
                        if (isCurrent) CyanHighlight else StudioBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onOpenProject(project) }
                        .testTag("library_project_card_${project.id}")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
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
                                        fontSize = 17.sp,
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
                                            text = if (project.isSampleProject) "SAMPLE" else "SAVED PROJECT",
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp,
                                            color = if (project.isSampleProject) CyanHighlight else ConfidenceEmerald
                                        )
                                    }
                                }
                                Text(
                                    text = project.artistOrSource,
                                    fontSize = 11.5.sp,
                                    color = TextMuted
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { onDeleteProject(project.id) },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Delete Project",
                                        tint = TextMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Open Project",
                                    tint = CyanHighlight
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(StudioBgDeep)
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Concert: ${project.detectedKey}",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = CyanHighlight
                            )
                            Text(
                                text = if (project.recommendedCapo > 0) {
                                    "Capo ${project.recommendedCapo} → ${project.playableKey} Shapes"
                                } else {
                                    "Open ${project.playableKey}"
                                },
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = ChordAmber
                            )
                            Text(
                                text = "${project.tempoBpm} BPM",
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    uiState: DontFretUiState,
    onToggleKeyTier: () -> Unit,
    onSelectModel: (GeminiModelOption) -> Unit,
    onSetAutoFailover: (Boolean) -> Unit,
    onSelectDefaultDifficulty: (GuitarDifficulty) -> Unit,
    onToggleKeepScreenAwake: () -> Unit,
    onOpenClassicHtmlWrapper: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    if (uiState.showClassicHtmlDemoModal) {
        ClassicHtmlWrapperModal(onDismiss = { onOpenClassicHtmlWrapper(false) })
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. DCI Brand Identity Card
        DciMetallicEmblemCard()

        // 2. Dual Gemini API Key Routing Architecture Card
        Surface(
            color = StudioSurface,
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.55f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = "Gemini Routing",
                        tint = ChordAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "AI / GEMINI DUAL-KEY ROUTING ARCHITECTURE",
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextIceWhite
                    )
                }

                Text(
                    text = uiState.keyRoutingStatus.statusDescription,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = if (uiState.keyRoutingStatus.isAnyKeyAvailable) ConfidenceEmerald else ChordAmber
                )

                Text(
                    text = "• FREE KEY (GEMINI_API_KEY_FREE): Low-cost text classification, light lyric/style diagnosis & cadence rewrites.\n" +
                        "• PAID KEY (GEMINI_API_KEY_PAID): Multimodal audio understanding, deep musical transcription & automatic quota failover.\n" +
                        "• Configure keys securely via the AI Studio Secrets Panel.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )

                // Active Key Tier Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isFree = uiState.activeKeyTier == "FREE"
                    Button(
                        onClick = { if (!isFree) onToggleKeyTier() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFree) ConfidenceEmerald else StudioBgDeep,
                            contentColor = if (isFree) StudioBgDeep else TextSecondary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "🟢 Free Key Tier",
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = { if (isFree) onToggleKeyTier() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!isFree) ChordAmber else StudioBgDeep,
                            contentColor = if (!isFree) StudioBgDeep else TextSecondary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "⚡ Paid Key Tier",
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Auto-Failover Switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(StudioBgDeep)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Automatic Paid Key Failover",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = TextIceWhite
                        )
                        Text(
                            text = "Fallback to backup key only when primary quota fails",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                    Switch(
                        checked = uiState.autoFailoverEnabled,
                        onCheckedChange = onSetAutoFailover,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = TextIceWhite,
                            checkedTrackColor = ElectricBlue
                        )
                    )
                }

                // Default Model Selector
                Text(
                    text = "DEFAULT GEMINI STUDIO MODEL",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = CyanHighlight
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GeminiModelOption.entries.forEach { model ->
                        FilterChip(
                            selected = uiState.selectedModel == model,
                            onClick = { onSelectModel(model) },
                            label = {
                                Text(
                                    text = "${model.displayName} (${model.badge})",
                                    fontSize = 11.sp
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricBlue.copy(alpha = 0.28f),
                                selectedLabelColor = CyanHighlight
                            )
                        )
                    }
                }

                // Security Warning Notice per android-secret-management / gemini-api skills
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(WarningCoral.copy(alpha = 0.12f))
                        .border(1.dp, WarningCoral.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Security Caution",
                        tint = WarningCoral,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Security Caution: Prototype builds access secrets via BuildConfig. Android APKs can be decompiled—do not share APK files containing active API keys publicly.",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }
        }

        // 3. Guitar & Stage Preferences Card
        Surface(
            color = StudioSurface,
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "GUITAR & PERFORMANCE PREFERENCES",
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = TextIceWhite
                )

                Text(
                    text = "Preferred Guitar Voicing Level:",
                    fontSize = 12.sp,
                    color = TextSecondary
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GuitarDifficulty.entries.forEach { diff ->
                        FilterChip(
                            selected = uiState.guitarDifficulty == diff,
                            onClick = { onSelectDefaultDifficulty(diff) },
                            label = { Text(diff.label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricBlue.copy(alpha = 0.28f),
                                selectedLabelColor = CyanHighlight
                            )
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(StudioBgDeep)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Keep Screen Awake on Song Sheet",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = TextIceWhite
                        )
                        Text(
                            text = "Prevents display dimming while playing guitar hands-free",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                    Switch(
                        checked = uiState.keepScreenAwake,
                        onCheckedChange = { onToggleKeepScreenAwake() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = TextIceWhite,
                            checkedTrackColor = ConfidenceEmerald
                        )
                    )
                }
            }
        }

        // 4. Classic HTML App Wrapper Card + DCI Brand Emblem Inspection
        Surface(
            color = StudioSurface,
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "CLASSIC HTML DEMO WRAPPER & DCI CREDITS",
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = TextIceWhite
                )
                Text(
                    text = "Don't Fret natively upgrades your ChordForge / GeminiGuitar HTML specification into a full Android studio suite. You can also launch the embedded HTML demo wrapper below.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Button(
                    onClick = { onOpenClassicHtmlWrapper(true) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StudioSurfaceElevated,
                        contentColor = CyanHighlight
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("launch_classic_html_wrapper_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = "Open HTML Demo Wrapper",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Open Embedded ChordForge HTML Demo Wrapper",
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ClassicHtmlWrapperModal(onDismiss: () -> Unit) {
    BackHandler { onDismiss() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioBgDeep)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(StudioSurface)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DciLogoMark(size = 24.dp)
                Text(
                    text = "ChordForge / Don't Fret HTML Demo Wrapper",
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = TextIceWhite
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close HTML Wrapper",
                    tint = CyanHighlight
                )
            }
        }

        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = WebViewClient()
                    loadUrl("file:///android_asset/chordforge_demo.html")
                }
            }
        )
    }
}
