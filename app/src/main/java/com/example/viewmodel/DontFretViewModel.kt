package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioDspEngine
import com.example.audio.ExoPlayerSheetController
import com.example.audio.ExoPlayerVisualizerFrame
import com.example.audio.GuitarMicRecorder
import com.example.audio.LocalDspResult
import com.example.data.DontFretDatabase
import com.example.data.ProjectRepository
import com.example.domain.MusicTheoryEngine
import com.example.model.AnalysisStage
import com.example.model.ChordVoicing
import com.example.model.GuitarDifficulty
import com.example.model.GuitarOption
import com.example.model.LineDiagnostic
import com.example.model.LineRewriteRequest
import com.example.model.LineRewriteSuggestion
import com.example.model.LyricDiagnosisReport
import com.example.model.SongAnalysisProject
import com.example.model.StudioChatMessage
import com.example.model.StudioChatRole
import com.example.model.SunoStyleAnalysis
import com.example.network.GeminiModelOption
import com.example.network.GeminiService
import com.example.network.KeyRoutingStatus
import com.example.util.SheetExportManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

enum class AppTab(val label: String) {
    SONG("Song"),
    SHEET("Sheet"),
    WORKSHOP("Workshop"),
    LIBRARY("Library"),
    SETTINGS("Settings")
}

data class DontFretUiState(
    val activeTab: AppTab = AppTab.SONG,
    val isPerformanceMode: Boolean = false,
    val showClassicHtmlDemoModal: Boolean = false,

    // Audio & Analysis Pipeline State
    val selectedAudioFileName: String? = null,
    val selectedAudioUriStr: String? = null,
    val localDspResult: LocalDspResult? = null,
    val isRecordingMic: Boolean = false,
    val recordingSeconds: Int = 0,
    val recordingAmplitude: Float = 0.1f,
    val optionalLyricsInput: String = "",
    val songwritingBriefInput: String = "",
    val isAnalyzing: Boolean = false,
    val analysisStage: AnalysisStage = AnalysisStage.IDLE,
    val statusMessage: String? = null,

    // Active Song Sheet, ExoPlayer Visualizer & Guitar Mode State
    val activeProject: SongAnalysisProject = ProjectRepository.buildStarterProjects().first(),
    val transposeSemitones: Int = 0,
    val guitarDifficulty: GuitarDifficulty = GuitarDifficulty.BEGINNER,
    val guitarOptions: List<GuitarOption> = emptyList(),
    val selectedGuitarOptionId: String = "easier_open",
    val sheetFontSizeSp: Float = 16f,
    val chordsOnlyView: Boolean = false,
    val collapsedSectionLabels: Set<String> = emptySet(),
    val autoScrollEnabled: Boolean = true,
    val keepScreenAwake: Boolean = true,
    val isPlayingAudio: Boolean = false,
    val playbackPositionSec: Float = 0f,
    val visualizerFrame: ExoPlayerVisualizerFrame = ExoPlayerVisualizerFrame(),
    val activeChordHelpModal: Pair<String, String>? = null, // (PlayedChord, SoundingChord)

    // Suno 6 Workshop & Multi-Turn Studio Coach Chat State
    val workshopSubTab: Int = 0, // 0 = Lyric Diagnosis, 1 = Suno Style Prompt, 2 = Studio Coach Chat
    val workshopLyricsDraft: String = "",
    val workshopStylePromptDraft: String = "",
    val workshopSongDescription: String = "",
    val lyricDiagnosis: LyricDiagnosisReport? = null,
    val selectedDiagnosticLine: LineDiagnostic? = null,
    val keepCadenceLock: Boolean = true,
    val selectedRewriteMode: String = "Keep cadence",
    val lineRewriteSuggestions: List<LineRewriteSuggestion> = emptyList(),
    val isRewritingLine: Boolean = false,
    val sunoStyleAnalysis: SunoStyleAnalysis? = null,
    val isAnalyzingStyle: Boolean = false,

    // Multi-Turn Gemini Chatbot State
    val activeChatRole: StudioChatRole = StudioChatRole.GUITAR_COACH,
    val chatMessages: List<StudioChatMessage> = emptyList(),
    val chatInputDraft: String = "",
    val isSendingChat: Boolean = false,

    // Library Search
    val librarySearchQuery: String = "",

    // AI / Gemini Routing Settings
    val activeKeyTier: String = "FREE", // "FREE" or "PAID"
    val selectedModel: GeminiModelOption = GeminiModelOption.FLASH,
    val autoFailoverEnabled: Boolean = true,
    val keyRoutingStatus: KeyRoutingStatus = GeminiService.getRoutingStatus("FREE", true)
)

class DontFretViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ProjectRepository
    private val micRecorder = GuitarMicRecorder(application.applicationContext)
    private var micTimerJob: Job? = null

    private val _uiState = MutableStateFlow(DontFretUiState())
    val uiState: StateFlow<DontFretUiState> = _uiState.asStateFlow()

    val savedProjects: StateFlow<List<SongAnalysisProject>>

    private val exoPlayerController: ExoPlayerSheetController

    init {
        val db = DontFretDatabase.getInstance(application.applicationContext)
        repository = ProjectRepository(db.projectDao())
        savedProjects = repository.allProjects.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ProjectRepository.buildStarterProjects()
        )

        exoPlayerController = ExoPlayerSheetController(
            context = application.applicationContext,
            scope = viewModelScope,
            onFrameUpdate = { frame ->
                _uiState.update {
                    it.copy(
                        isPlayingAudio = frame.isPlaying,
                        playbackPositionSec = frame.positionSec,
                        visualizerFrame = frame
                    )
                }
            },
            onPlaybackCompleted = {
                _uiState.update {
                    it.copy(
                        isPlayingAudio = false,
                        playbackPositionSec = 0f
                    )
                }
            }
        )

        viewModelScope.launch {
            repository.ensureSeedProjects()
        }

        val starter = ProjectRepository.buildStarterProjects().first()
        loadProjectIntoStudio(starter, switchToSheet = false)
    }

    fun selectTab(tab: AppTab) {
        _uiState.update { it.copy(activeTab = tab, isPerformanceMode = false) }
    }

    fun setPerformanceMode(enabled: Boolean) {
        _uiState.update {
            it.copy(
                isPerformanceMode = enabled,
                activeTab = if (enabled) AppTab.SHEET else it.activeTab
            )
        }
    }

    fun setShowClassicHtmlDemoModal(show: Boolean) {
        _uiState.update { it.copy(showClassicHtmlDemoModal = show) }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    // =========================================================================
    // AI / GEMINI DUAL-KEY ROUTING CONTROLS
    // =========================================================================

    fun toggleKeyTier() {
        val nextTier = if (_uiState.value.activeKeyTier == "FREE") "PAID" else "FREE"
        val status = GeminiService.getRoutingStatus(nextTier, _uiState.value.autoFailoverEnabled)
        _uiState.update {
            it.copy(
                activeKeyTier = nextTier,
                keyRoutingStatus = status,
                statusMessage = "Switched to $nextTier Gemini routing tier"
            )
        }
    }

    fun setModelOption(model: GeminiModelOption) {
        _uiState.update { it.copy(selectedModel = model) }
    }

    fun setAutoFailover(enabled: Boolean) {
        val status = GeminiService.getRoutingStatus(_uiState.value.activeKeyTier, enabled)
        _uiState.update {
            it.copy(autoFailoverEnabled = enabled, keyRoutingStatus = status)
        }
    }

    // =========================================================================
    // AUDIO UPLOAD, MICROPHONE RECORDING & ANALYSIS PIPELINE
    // =========================================================================

    fun updateOptionalLyricsInput(text: String) {
        _uiState.update { it.copy(optionalLyricsInput = text) }
    }

    fun updateSongwritingBriefInput(text: String) {
        _uiState.update { it.copy(songwritingBriefInput = text) }
    }

    fun onAudioFilePicked(uri: Uri, displayName: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    selectedAudioFileName = displayName,
                    selectedAudioUriStr = uri.toString(),
                    analysisStage = AnalysisStage.READING_AUDIO,
                    statusMessage = "Running local DSP harmonic & tuning analysis on $displayName..."
                )
            }
            val dsp = AudioDspEngine.analyzeAudioUri(
                context = getApplication(),
                uri = uri,
                fileName = displayName
            )
            _uiState.update {
                it.copy(
                    localDspResult = dsp,
                    analysisStage = AnalysisStage.IDLE,
                    statusMessage = "Local DSP ready: ${dsp.detectedKey} (${dsp.estimatedBpm} BPM)"
                )
            }
        }
    }

    fun toggleMicRecording() {
        if (_uiState.value.isRecordingMic) {
            stopMicRecordingAndPrepare()
        } else {
            startMicRecording()
        }
    }

    private fun startMicRecording() {
        val out = micRecorder.startRecording()
        if (out == null) {
            _uiState.update {
                it.copy(statusMessage = "Unable to start microphone recording. Check permission.")
            }
            return
        }
        _uiState.update {
            it.copy(
                isRecordingMic = true,
                recordingSeconds = 0,
                recordingAmplitude = 0.2f,
                statusMessage = "Recording guitar / audio input..."
            )
        }
        micTimerJob?.cancel()
        micTimerJob = viewModelScope.launch {
            var ticks = 0
            while (isActive && _uiState.value.isRecordingMic) {
                delay(250L)
                ticks++
                val amp = micRecorder.getMaxAmplitudeNormalized()
                _uiState.update {
                    it.copy(
                        recordingSeconds = ticks / 4,
                        recordingAmplitude = amp
                    )
                }
            }
        }
    }

    private fun stopMicRecordingAndPrepare() {
        micTimerJob?.cancel()
        val recordedFile = micRecorder.stopRecording()
        _uiState.update { it.copy(isRecordingMic = false, recordingAmplitude = 0.05f) }
        if (recordedFile != null && recordedFile.exists()) {
            val uri = Uri.fromFile(recordedFile)
            onAudioFilePicked(uri, recordedFile.name)
        }
    }

    fun runFullSongAnalysis() {
        val state = _uiState.value
        if (state.isAnalyzing) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isAnalyzing = true,
                    analysisStage = AnalysisStage.READING_AUDIO,
                    statusMessage = null
                )
            }
            delay(320L)

            val fileName = state.selectedAudioFileName ?: "Studio Guitar Session.wav"
            val dsp = state.localDspResult ?: LocalDspResult(
                detectedKey = "Bb Major",
                dominantNotes = listOf("Bb", "Eb", "F", "G"),
                tuningOffsetCents = 0,
                tuningDescription = "Standard (E A D G B E) • +0c A440",
                estimatedBpm = 118,
                durationSec = 180,
                rootTimeline = listOf("Bb", "Eb", "Gm", "F"),
                summaryReport = "Dominant Notes: [Bb, Eb, F, G] | Tuning Offset: +0c A440 | Est. Tempo: 118 BPM",
                audioBase64 = "",
                mimeType = "audio/wav"
            )

            _uiState.update { it.copy(analysisStage = AnalysisStage.FINDING_TEMPO) }
            delay(320L)

            _uiState.update { it.copy(analysisStage = AnalysisStage.DETECTING_KEY) }
            delay(320L)

            _uiState.update { it.copy(analysisStage = AnalysisStage.MAPPING_CHORDS) }

            val effectiveTier = if (state.keyRoutingStatus.hasPaidKey) "PAID" else state.activeKeyTier
            val aiResult = GeminiService.analyzeAudioTrack(
                fileName = fileName,
                audioUriStr = state.selectedAudioUriStr,
                dspResult = dsp,
                userSuppliedLyrics = state.optionalLyricsInput,
                songwritingPrompt = state.songwritingBriefInput,
                modelOption = state.selectedModel,
                preferredTier = effectiveTier,
                autoFailover = state.autoFailoverEnabled
            )

            _uiState.update { it.copy(analysisStage = AnalysisStage.FINDING_SECTIONS) }
            delay(280L)

            _uiState.update { it.copy(analysisStage = AnalysisStage.BUILDING_GUITAR) }
            delay(280L)

            _uiState.update { it.copy(analysisStage = AnalysisStage.PREPARING_SHEET) }
            delay(240L)

            val finalProject = aiResult.getOrElse {
                GeminiService.buildLocalDspFallbackProject(
                    fileName = fileName,
                    audioUriStr = state.selectedAudioUriStr,
                    dsp = dsp,
                    userLyrics = state.optionalLyricsInput
                )
            }

            repository.saveProject(finalProject)
            loadProjectIntoStudio(finalProject, switchToSheet = true)

            val completionNotice = if (aiResult.isSuccess) {
                "Song sheet ready • Analysed with ${state.selectedModel.displayName}"
            } else {
                "Built Song Sheet via Local DSP & Deterministic Capo Engine (Configure Gemini API key in Secrets for full vocal AI transcription)"
            }

            _uiState.update {
                it.copy(
                    isAnalyzing = false,
                    analysisStage = AnalysisStage.IDLE,
                    statusMessage = completionNotice
                )
            }
        }
    }

    // =========================================================================
    // SONG SHEET, EXOPLAYER VISUALIZER, GUITAR MODE, CAPO & TRANSPOSE
    // =========================================================================

    fun loadProjectIntoStudio(project: SongAnalysisProject, switchToSheet: Boolean = true) {
        val options = MusicTheoryEngine.calculateGuitarOptions(
            originalSoundingKey = project.detectedKey,
            soundingChords = project.chordsUsed,
            userTransposeSemitones = 0
        )
        val defaultOpt = pickOptionForDifficulty(options, _uiState.value.guitarDifficulty)
        val combinedLyrics = project.sections.joinToString("\n\n") { sec ->
            "${sec.label}\n${sec.lines.joinToString("\n")}"
        }
        val diagnosis = project.lyricAnalysis ?: MusicTheoryEngine.analyzeLyricsDeterministically(combinedLyrics)

        val customPeaks = _uiState.value.localDspResult
            ?.takeIf { _uiState.value.selectedAudioUriStr == project.audioUriOrPath }
            ?.waveformPeaks ?: emptyList()

        exoPlayerController.loadProjectAudio(
            projectId = project.id,
            uriOrPath = project.audioUriOrPath,
            fallbackDurationSec = project.durationSec,
            chordEvents = project.chordEvents,
            customWaveformPeaks = customPeaks
        )

        val welcomeMsg = StudioChatMessage(
            id = "welcome_${project.id}",
            isUser = false,
            text = "Loaded **${project.title}** (${project.detectedKey} • ${project.tempoBpm} BPM). With **Capo ${defaultOpt.capoFret}**, you can play this using **${defaultOpt.playedShapes.joinToString(" – ")}** shapes. Ask me anything about chord transitions, strumming groove, Suno v6 tags, or lyric cadence!",
            roleLabel = _uiState.value.activeChatRole.title,
            modelUsed = "Studio Context"
        )

        _uiState.update {
            it.copy(
                activeProject = project,
                transposeSemitones = 0,
                guitarOptions = options,
                selectedGuitarOptionId = defaultOpt.id,
                isPlayingAudio = false,
                playbackPositionSec = 0f,
                collapsedSectionLabels = emptySet(),
                workshopLyricsDraft = combinedLyrics,
                workshopStylePromptDraft = project.styleAnalysis.originalPrompt.ifBlank { project.sunoPrompt.lines().firstOrNull() ?: "" },
                lyricDiagnosis = diagnosis,
                selectedDiagnosticLine = diagnosis.lineDiagnostics.firstOrNull(),
                sunoStyleAnalysis = project.styleAnalysis,
                chatMessages = listOf(welcomeMsg),
                activeTab = if (switchToSheet) AppTab.SHEET else it.activeTab
            )
        }
    }

    fun transposeBySemitones(delta: Int) {
        val current = _uiState.value
        val nextShift = ((current.transposeSemitones + delta + 12) % 12).let {
            if (it > 6) it - 12 else it
        }
        val options = MusicTheoryEngine.calculateGuitarOptions(
            originalSoundingKey = current.activeProject.detectedKey,
            soundingChords = current.activeProject.chordsUsed,
            userTransposeSemitones = nextShift
        )
        val matchingOpt = options.find { it.id == current.selectedGuitarOptionId }
            ?: pickOptionForDifficulty(options, current.guitarDifficulty)

        _uiState.update {
            it.copy(
                transposeSemitones = nextShift,
                guitarOptions = options,
                selectedGuitarOptionId = matchingOpt.id
            )
        }
    }

    fun resetTranspose() {
        val current = _uiState.value
        val options = MusicTheoryEngine.calculateGuitarOptions(
            originalSoundingKey = current.activeProject.detectedKey,
            soundingChords = current.activeProject.chordsUsed,
            userTransposeSemitones = 0
        )
        val matchingOpt = options.find { it.id == current.selectedGuitarOptionId } ?: options.first()
        _uiState.update {
            it.copy(
                transposeSemitones = 0,
                guitarOptions = options,
                selectedGuitarOptionId = matchingOpt.id
            )
        }
    }

    fun setGuitarDifficulty(difficulty: GuitarDifficulty) {
        val options = _uiState.value.guitarOptions
        val picked = pickOptionForDifficulty(options, difficulty)
        _uiState.update {
            it.copy(
                guitarDifficulty = difficulty,
                selectedGuitarOptionId = picked.id
            )
        }
    }

    fun selectGuitarOption(optionId: String) {
        _uiState.update { it.copy(selectedGuitarOptionId = optionId) }
    }

    private fun pickOptionForDifficulty(
        options: List<GuitarOption>,
        difficulty: GuitarDifficulty
    ): GuitarOption {
        if (options.isEmpty()) {
            return GuitarOption(
                id = "original",
                name = "Original",
                soundingKey = "G Major",
                playedKeyShape = "G Major",
                capoFret = 0,
                playedShapes = listOf("G", "C", "D", "Em"),
                chordMapping = emptyMap(),
                difficulty = GuitarDifficulty.BEGINNER,
                barreChordCount = 0,
                openChordCount = 4,
                summary = "Standard open chords"
            )
        }
        return when (difficulty) {
            GuitarDifficulty.BEGINNER -> options.find { it.id == "easier_open" }
                ?: options.find { it.id == "easier_beginner" }
                ?: options.first()
            GuitarDifficulty.INTERMEDIATE -> options.find { it.id == "minimal_barre" }
                ?: options.find { it.id == "easier_open" }
                ?: options.first()
            GuitarDifficulty.ORIGINAL_EXACT -> options.find { it.id == "original" } ?: options.first()
        }
    }

    fun adjustSheetFontSize(deltaSp: Float) {
        _uiState.update {
            it.copy(sheetFontSizeSp = (it.sheetFontSizeSp + deltaSp).coerceIn(12f, 24f))
        }
    }

    fun toggleChordsOnlyView() {
        _uiState.update { it.copy(chordsOnlyView = !it.chordsOnlyView) }
    }

    fun toggleSectionCollapse(sectionLabel: String) {
        _uiState.update { state ->
            val next = state.collapsedSectionLabels.toMutableSet()
            if (!next.add(sectionLabel)) {
                next.remove(sectionLabel)
            }
            state.copy(collapsedSectionLabels = next)
        }
    }

    fun toggleAutoScroll() {
        _uiState.update { it.copy(autoScrollEnabled = !it.autoScrollEnabled) }
    }

    fun toggleKeepScreenAwake() {
        _uiState.update { it.copy(keepScreenAwake = !it.keepScreenAwake) }
    }

    fun openChordHelpModal(playedChord: String, soundingChord: String) {
        _uiState.update { it.copy(activeChordHelpModal = playedChord to soundingChord) }
        val activeOpt = getActiveGuitarOption()
        val voicing = MusicTheoryEngine.getVoicingsForChord(playedChord).first()
        strumChordVoicing(voicing, activeOpt.capoFret)
    }

    fun closeChordHelpModal() {
        _uiState.update { it.copy(activeChordHelpModal = null) }
    }

    fun strumChordVoicing(voicing: ChordVoicing, capoFret: Int = getActiveGuitarOption().capoFret) {
        viewModelScope.launch {
            AudioDspEngine.playGuitarChordStrum(voicing, capoFret)
        }
    }

    fun getActiveGuitarOption(): GuitarOption {
        val state = _uiState.value
        return state.guitarOptions.find { it.id == state.selectedGuitarOptionId }
            ?: state.guitarOptions.firstOrNull()
            ?: MusicTheoryEngine.calculateGuitarOptions(
                state.activeProject.detectedKey,
                state.activeProject.chordsUsed,
                state.transposeSemitones
            ).first()
    }

    // =========================================================================
    // EXOPLAYER PLAYBACK, SPEED, SECTION SEEKING & A-B LOOPING
    // =========================================================================

    fun togglePlayback() {
        val playing = exoPlayerController.togglePlayPause()
        _uiState.update { it.copy(isPlayingAudio = playing) }
    }

    fun seekToSeconds(seconds: Float) {
        exoPlayerController.seekToSeconds(seconds)
        _uiState.update { it.copy(playbackPositionSec = seconds) }
    }

    fun setPlaybackSpeed(speed: Float) {
        exoPlayerController.setPlaybackSpeed(speed)
    }

    fun toggleSectionLoop(startSec: Float, endSec: Float) {
        exoPlayerController.toggleSectionLoop(startSec, endSec)
    }

    fun clearSectionLoop() {
        exoPlayerController.clearSectionLoop()
    }

    // =========================================================================
    // SUNO 6 WORKSHOP & MULTI-TURN STUDIO COACH CHATBOT
    // =========================================================================

    fun setWorkshopSubTab(index: Int) {
        _uiState.update { it.copy(workshopSubTab = index) }
    }

    fun updateWorkshopLyricsDraft(lyrics: String) {
        _uiState.update { it.copy(workshopLyricsDraft = lyrics) }
    }

    fun updateWorkshopStylePromptDraft(prompt: String) {
        _uiState.update { it.copy(workshopStylePromptDraft = prompt) }
    }

    fun populateWorkshopFromActiveTrack() {
        val proj = _uiState.value.activeProject
        val combined = proj.sections.joinToString("\n\n") { sec ->
            "${sec.label}\n${sec.lines.joinToString("\n")}"
        }
        val report = MusicTheoryEngine.analyzeLyricsDeterministically(combined)
        _uiState.update {
            it.copy(
                workshopLyricsDraft = combined,
                workshopStylePromptDraft = proj.styleAnalysis.conciseRecommendedPrompt,
                lyricDiagnosis = report,
                selectedDiagnosticLine = report.lineDiagnostics.firstOrNull(),
                statusMessage = "Loaded lyrics & style from \"${proj.title}\""
            )
        }
    }

    fun runLyricDiagnosis() {
        val raw = _uiState.value.workshopLyricsDraft
        val report = MusicTheoryEngine.analyzeLyricsDeterministically(raw)
        _uiState.update {
            it.copy(
                lyricDiagnosis = report,
                selectedDiagnosticLine = report.lineDiagnostics.firstOrNull(),
                lineRewriteSuggestions = emptyList(),
                statusMessage = "Lyric diagnosis complete (${report.lineDiagnostics.size} lines analysed)"
            )
        }
    }

    fun selectDiagnosticLineForRewrite(line: LineDiagnostic) {
        _uiState.update {
            it.copy(
                selectedDiagnosticLine = line,
                lineRewriteSuggestions = emptyList()
            )
        }
        requestLineRewrites(line, _uiState.value.selectedRewriteMode)
    }

    fun toggleKeepCadenceLock() {
        val next = !_uiState.value.keepCadenceLock
        _uiState.update { it.copy(keepCadenceLock = next) }
        _uiState.value.selectedDiagnosticLine?.let { line ->
            requestLineRewrites(line, _uiState.value.selectedRewriteMode)
        }
    }

    fun selectRewriteMode(mode: String) {
        val forceCadence = mode.equals("Keep cadence", ignoreCase = true) || _uiState.value.keepCadenceLock
        _uiState.update {
            it.copy(
                selectedRewriteMode = mode,
                keepCadenceLock = if (mode.equals("Keep cadence", ignoreCase = true)) true else forceCadence
            )
        }
        _uiState.value.selectedDiagnosticLine?.let { line ->
            requestLineRewrites(line, mode)
        }
    }

    private fun requestLineRewrites(line: LineDiagnostic, mode: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRewritingLine = true) }
            val state = _uiState.value
            val req = LineRewriteRequest(
                originalLine = line.text,
                sectionContext = line.sectionLabel,
                mode = mode,
                keepCadence = state.keepCadenceLock,
                targetSyllables = line.syllableCount,
                targetStressPattern = line.stressPattern
            )
            val suggestions = GeminiService.rewriteWorkshopLine(
                request = req,
                preferredTier = state.activeKeyTier,
                autoFailover = state.autoFailoverEnabled
            )
            _uiState.update {
                it.copy(
                    isRewritingLine = false,
                    lineRewriteSuggestions = suggestions
                )
            }
        }
    }

    fun applySuggestedLineToDraft(suggestion: LineRewriteSuggestion) {
        val state = _uiState.value
        val targetLine = state.selectedDiagnosticLine ?: return
        val updatedDraft = state.workshopLyricsDraft.replaceFirst(targetLine.text, suggestion.text)
        val newReport = MusicTheoryEngine.analyzeLyricsDeterministically(updatedDraft)
        val newSelected = newReport.lineDiagnostics.getOrNull(targetLine.lineIndex)
        _uiState.update {
            it.copy(
                workshopLyricsDraft = updatedDraft,
                lyricDiagnosis = newReport,
                selectedDiagnosticLine = newSelected,
                statusMessage = "Applied line edit (${suggestion.syllableCount} syllables)"
            )
        }
    }

    fun runSunoStyleAnalysis() {
        val prompt = _uiState.value.workshopStylePromptDraft
        viewModelScope.launch {
            _uiState.update { it.copy(isAnalyzingStyle = true) }
            val state = _uiState.value
            val result = GeminiService.analyzeSunoStyle(
                rawStylePrompt = prompt,
                preferredTier = state.activeKeyTier,
                autoFailover = state.autoFailoverEnabled
            )
            _uiState.update {
                it.copy(
                    isAnalyzingStyle = false,
                    sunoStyleAnalysis = result,
                    statusMessage = "Suno 6 style prompt analysed"
                )
            }
        }
    }

    fun applyRecommendedStylePrompt(newPrompt: String) {
        _uiState.update {
            it.copy(
                workshopStylePromptDraft = newPrompt,
                statusMessage = "Updated editable style prompt draft"
            )
        }
    }

    // Multi-Turn Chatbot Actions
    fun selectChatRole(role: StudioChatRole) {
        _uiState.update { it.copy(activeChatRole = role) }
    }

    fun updateChatInputDraft(text: String) {
        _uiState.update { it.copy(chatInputDraft = text) }
    }

    fun sendStudioChatMessage(quickPrompt: String? = null) {
        val state = _uiState.value
        val messageText = (quickPrompt ?: state.chatInputDraft).trim()
        if (messageText.isEmpty() || state.isSendingChat) return

        val userMsg = StudioChatMessage(
            id = UUID.randomUUID().toString(),
            isUser = true,
            text = messageText
        )
        val historyBeforeSend = state.chatMessages

        _uiState.update {
            it.copy(
                chatMessages = it.chatMessages + userMsg,
                chatInputDraft = if (quickPrompt == null) "" else it.chatInputDraft,
                isSendingChat = true
            )
        }

        viewModelScope.launch {
            val activeOpt = getActiveGuitarOption()
            val replyText = GeminiService.sendStudioChatTurn(
                history = historyBeforeSend,
                newUserMessage = messageText,
                role = _uiState.value.activeChatRole,
                activeProject = _uiState.value.activeProject,
                activeCapoFret = activeOpt.capoFret,
                activePlayedKey = activeOpt.playedKeyShape,
                modelOption = _uiState.value.selectedModel,
                preferredTier = _uiState.value.activeKeyTier,
                autoFailover = _uiState.value.autoFailoverEnabled
            )

            val modelMsg = StudioChatMessage(
                id = UUID.randomUUID().toString(),
                isUser = false,
                text = replyText,
                roleLabel = _uiState.value.activeChatRole.title,
                modelUsed = _uiState.value.selectedModel.displayName
            )
            _uiState.update {
                it.copy(
                    chatMessages = it.chatMessages + modelMsg,
                    isSendingChat = false
                )
            }
        }
    }

    // =========================================================================
    // LIBRARY & EXPORT ACTIONS
    // =========================================================================

    fun updateLibrarySearchQuery(query: String) {
        _uiState.update { it.copy(librarySearchQuery = query) }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            repository.deleteProject(projectId)
            _uiState.update { it.copy(statusMessage = "Removed project from library") }
        }
    }

    fun exportPrintablePdf() {
        val state = _uiState.value
        val res = SheetExportManager.exportAndSharePrintablePdf(
            context = getApplication(),
            project = state.activeProject,
            activeOption = getActiveGuitarOption(),
            transposeSemitones = state.transposeSemitones
        )
        if (res.isFailure) {
            _uiState.update {
                it.copy(statusMessage = "PDF generated in cache (${res.exceptionOrNull()?.message ?: "Ready"})")
            }
        }
    }

    fun copyPlainTextSheetToClipboard() {
        val state = _uiState.value
        val txt = SheetExportManager.buildPlainTextSongSheet(
            project = state.activeProject,
            activeOption = getActiveGuitarOption(),
            transposeSemitones = state.transposeSemitones
        )
        SheetExportManager.copyToClipboard(getApplication(), "${state.activeProject.title} Chords", txt)
        _uiState.update { it.copy(statusMessage = "Copied Plain Text Chords + Lyrics to clipboard!") }
    }

    fun copyMarkdownSheetToClipboard() {
        val state = _uiState.value
        val md = SheetExportManager.buildMarkdownSongSheet(
            project = state.activeProject,
            activeOption = getActiveGuitarOption(),
            transposeSemitones = state.transposeSemitones
        )
        SheetExportManager.copyToClipboard(getApplication(), "${state.activeProject.title} Markdown", md)
        _uiState.update { it.copy(statusMessage = "Copied Markdown Song Sheet to clipboard!") }
    }

    override fun onCleared() {
        super.onCleared()
        micTimerJob?.cancel()
        micRecorder.stopRecording()
        exoPlayerController.release()
    }
}
