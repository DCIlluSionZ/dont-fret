package com.example

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.DciSignatureFooter
import com.example.ui.components.DontFretTopBar
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SongHomeScreen
import com.example.ui.screens.SongSheetScreen
import com.example.ui.screens.WorkshopScreen
import com.example.ui.theme.ChordAmber
import com.example.ui.theme.CyanHighlight
import com.example.ui.theme.DontFretTheme
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.StudioBgDeep
import com.example.ui.theme.StudioBgPrimary
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.TextIceWhite
import com.example.ui.theme.TextMuted
import com.example.viewmodel.AppTab
import com.example.viewmodel.DontFretViewModel
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DontFretTheme {
                DontFretStudioApp()
            }
        }
    }
}

@Composable
fun DontFretStudioApp(
    viewModel: DontFretViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val savedProjects by viewModel.savedProjects.collectAsStateWithLifecycle()
    val activeGuitarOption = viewModel.getActiveGuitarOption()
    val context = LocalContext.current

    // Keep screen awake when viewing Song Sheet or in Performance Mode
    DisposableEffect(uiState.keepScreenAwake, uiState.activeTab, uiState.isPerformanceMode) {
        val window = (context as? Activity)?.window
        val shouldKeepAwake = uiState.keepScreenAwake &&
            (uiState.activeTab == AppTab.SHEET || uiState.isPerformanceMode)
        if (shouldKeepAwake) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Auto-dismiss transient status toast after 4 seconds
    LaunchedEffect(uiState.statusMessage) {
        if (uiState.statusMessage != null) {
            delay(4000L)
            viewModel.clearStatusMessage()
        }
    }

    // Back navigation returns to Song Home tab from secondary tabs
    if (uiState.activeTab != AppTab.SONG && !uiState.isPerformanceMode && !uiState.showClassicHtmlDemoModal) {
        BackHandler {
            viewModel.selectTab(AppTab.SONG)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioBgPrimary)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        val isExpandedScreen = maxWidth >= 640.dp

        Scaffold(
            containerColor = StudioBgPrimary,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                if (!uiState.isPerformanceMode) {
                    DontFretTopBar(
                        activeKeyTier = uiState.activeKeyTier,
                        isKeyConfigured = uiState.keyRoutingStatus.isAnyKeyAvailable,
                        onToggleKeyTier = viewModel::toggleKeyTier,
                        onEnterPerformanceMode = if (uiState.activeTab == AppTab.SHEET) {
                            { viewModel.setPerformanceMode(true) }
                        } else {
                            null
                        }
                    )
                }
            },
            bottomBar = {
                if (!uiState.isPerformanceMode) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Status Toast Banner
                        AnimatedVisibility(visible = uiState.statusMessage != null) {
                            Surface(
                                color = StudioSurfaceElevated,
                                border = androidx.compose.foundation.BorderStroke(1.dp, CyanHighlight.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.clearStatusMessage() }
                            ) {
                                Text(
                                    text = uiState.statusMessage ?: "",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 11.sp,
                                    color = ChordAmber,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                )
                            }
                        }

                        // Persistent DCI Brand Signature Footer ("DCI • DESIGNED & CODED with INTELLIGENCE")
                        DciSignatureFooter(
                            onClickBrandInfo = { viewModel.selectTab(AppTab.SETTINGS) }
                        )

                        // Mobile Bottom Navigation Bar (5 Tabs: Song, Sheet, Workshop, Library, Settings)
                        if (!isExpandedScreen) {
                            DontFretBottomBar(
                                activeTab = uiState.activeTab,
                                onSelectTab = viewModel::selectTab
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isExpandedScreen && !uiState.isPerformanceMode) {
                    DontFretNavigationRail(
                        activeTab = uiState.activeTab,
                        onSelectTab = viewModel::selectTab
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    val contentModifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 880.dp)

                    when (uiState.activeTab) {
                        AppTab.SONG -> SongHomeScreen(
                            uiState = uiState,
                            recentProjects = savedProjects,
                            onPickAudioUri = viewModel::onAudioFilePicked,
                            onToggleMicRecording = viewModel::toggleMicRecording,
                            onUpdateLyricsInput = viewModel::updateOptionalLyricsInput,
                            onSelectModel = viewModel::setModelOption,
                            onRunAnalysis = viewModel::runFullSongAnalysis,
                            onOpenProject = { proj -> viewModel.loadProjectIntoStudio(proj, switchToSheet = true) },
                            modifier = contentModifier
                        )

                        AppTab.SHEET -> SongSheetScreen(
                            uiState = uiState,
                            activeGuitarOption = activeGuitarOption,
                            onSelectDifficulty = viewModel::setGuitarDifficulty,
                            onSelectGuitarOption = viewModel::selectGuitarOption,
                            onTransposeDelta = viewModel::transposeBySemitones,
                            onResetTranspose = viewModel::resetTranspose,
                            onAdjustFontSize = viewModel::adjustSheetFontSize,
                            onToggleChordsOnly = viewModel::toggleChordsOnlyView,
                            onToggleSectionCollapse = viewModel::toggleSectionCollapse,
                            onToggleAutoScroll = viewModel::toggleAutoScroll,
                            onTogglePerformanceMode = viewModel::setPerformanceMode,
                            onTogglePlayback = viewModel::togglePlayback,
                            onSeekToSeconds = viewModel::seekToSeconds,
                            onSetPlaybackSpeed = viewModel::setPlaybackSpeed,
                            onToggleSectionLoop = viewModel::toggleSectionLoop,
                            onClearSectionLoop = viewModel::clearSectionLoop,
                            onOpenChordHelp = viewModel::openChordHelpModal,
                            onCloseChordHelp = viewModel::closeChordHelpModal,
                            onStrumVoicing = { voicing -> viewModel.strumChordVoicing(voicing) },
                            onExportPdf = viewModel::exportPrintablePdf,
                            onCopyMarkdown = viewModel::copyMarkdownSheetToClipboard,
                            onCopyPlainText = viewModel::copyPlainTextSheetToClipboard,
                            modifier = contentModifier
                        )

                        AppTab.WORKSHOP -> WorkshopScreen(
                            uiState = uiState,
                            onSelectSubTab = viewModel::setWorkshopSubTab,
                            onPopulateFromTrack = viewModel::populateWorkshopFromActiveTrack,
                            onUpdateLyricsDraft = viewModel::updateWorkshopLyricsDraft,
                            onRunLyricDiagnosis = viewModel::runLyricDiagnosis,
                            onSelectLineForRewrite = viewModel::selectDiagnosticLineForRewrite,
                            onToggleKeepCadence = viewModel::toggleKeepCadenceLock,
                            onSelectRewriteMode = viewModel::selectRewriteMode,
                            onApplyLineSuggestion = viewModel::applySuggestedLineToDraft,
                            onUpdateStylePromptDraft = viewModel::updateWorkshopStylePromptDraft,
                            onRunStyleAnalysis = viewModel::runSunoStyleAnalysis,
                            onApplyRecommendedStylePrompt = viewModel::applyRecommendedStylePrompt,
                            modifier = contentModifier
                        )

                        AppTab.LIBRARY -> LibraryScreen(
                            uiState = uiState,
                            projects = savedProjects,
                            onUpdateSearch = viewModel::updateLibrarySearchQuery,
                            onOpenProject = { proj -> viewModel.loadProjectIntoStudio(proj, switchToSheet = true) },
                            onDeleteProject = viewModel::deleteProject,
                            modifier = contentModifier
                        )

                        AppTab.SETTINGS -> SettingsScreen(
                            uiState = uiState,
                            onToggleKeyTier = viewModel::toggleKeyTier,
                            onSelectModel = viewModel::setModelOption,
                            onSetAutoFailover = viewModel::setAutoFailover,
                            onSelectDefaultDifficulty = viewModel::setGuitarDifficulty,
                            onToggleKeepScreenAwake = viewModel::toggleKeepScreenAwake,
                            onOpenClassicHtmlWrapper = viewModel::setShowClassicHtmlDemoModal,
                            modifier = contentModifier
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DontFretBottomBar(
    activeTab: AppTab,
    onSelectTab: (AppTab) -> Unit
) {
    NavigationBar(
        containerColor = StudioBgDeep,
        contentColor = TextIceWhite,
        tonalElevation = 0.dp
    ) {
        AppTab.entries.forEach { tab ->
            val selected = tab == activeTab
            NavigationBarItem(
                selected = selected,
                onClick = { onSelectTab(tab) },
                icon = {
                    Icon(
                        imageVector = getTabIcon(tab, selected),
                        contentDescription = tab.label
                    )
                },
                label = {
                    Text(
                        text = tab.label,
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 11.sp
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = CyanHighlight,
                    selectedTextColor = CyanHighlight,
                    indicatorColor = ElectricBlue.copy(alpha = 0.24f),
                    unselectedIconColor = TextMuted,
                    unselectedTextColor = TextMuted
                ),
                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
            )
        }
    }
}

@Composable
private fun DontFretNavigationRail(
    activeTab: AppTab,
    onSelectTab: (AppTab) -> Unit
) {
    NavigationRail(
        containerColor = StudioBgDeep,
        contentColor = TextIceWhite
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
        ) {
            AppTab.entries.forEach { tab ->
                val selected = tab == activeTab
                NavigationRailItem(
                    selected = selected,
                    onClick = { onSelectTab(tab) },
                    icon = {
                        Icon(
                            imageVector = getTabIcon(tab, selected),
                            contentDescription = tab.label
                        )
                    },
                    label = {
                        Text(
                            text = tab.label,
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 11.sp
                        )
                    },
                    colors = NavigationRailItemDefaults.colors(
                        selectedIconColor = CyanHighlight,
                        selectedTextColor = CyanHighlight,
                        indicatorColor = ElectricBlue.copy(alpha = 0.24f),
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted
                    ),
                    modifier = Modifier.testTag("rail_tab_${tab.name.lowercase()}")
                )
            }
        }
    }
}

private fun getTabIcon(tab: AppTab, selected: Boolean): ImageVector {
    return when (tab) {
        AppTab.SONG -> if (selected) Icons.Filled.Album else Icons.Outlined.Album
        AppTab.SHEET -> if (selected) Icons.AutoMirrored.Filled.QueueMusic else Icons.AutoMirrored.Outlined.QueueMusic
        AppTab.WORKSHOP -> if (selected) Icons.Filled.AutoFixHigh else Icons.Outlined.AutoFixHigh
        AppTab.LIBRARY -> if (selected) Icons.Filled.LibraryMusic else Icons.Outlined.LibraryMusic
        AppTab.SETTINGS -> if (selected) Icons.Filled.Settings else Icons.Outlined.Settings
    }
}
