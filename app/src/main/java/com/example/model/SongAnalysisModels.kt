package com.example.model

import kotlinx.serialization.Serializable

@Serializable
enum class GuitarDifficulty(val label: String) {
    BEGINNER("Beginner"),
    INTERMEDIATE("Intermediate"),
    ORIGINAL_EXACT("Original / Exact")
}

@Serializable
enum class AnalysisStage(val title: String, val detail: String) {
    IDLE("Ready", "Drop in an audio track or record your guitar"),
    READING_AUDIO("Reading audio", "Decoding audio stream & PCM waveform buffers..."),
    FINDING_TEMPO("Finding tempo", "Running onset envelope & beat pulse detection..."),
    DETECTING_KEY("Detecting key", "Computing Goertzel harmonic chroma & A440 tuning offset..."),
    MAPPING_CHORDS("Mapping chord changes", "Aligning harmonic transitions with Gemini studio ear..."),
    FINDING_SECTIONS("Finding song sections", "Segmenting Intro, Verse, Chorus, Bridge & Outro..."),
    BUILDING_GUITAR("Building guitar version", "Calculating deterministic Capo & open-chord equivalents..."),
    PREPARING_SHEET("Preparing song sheet", "Formatting playable lead sheet, tabs & diagrams...")
}

@Serializable
data class ConfidenceMetrics(
    val overallPercent: Int = 92,
    val keyConfidencePercent: Int = 94,
    val tempoConfidencePercent: Int = 90,
    val timingLockLabel: String = "High Lock"
)

@Serializable
data class SongSection(
    val type: String = "Verse", // Intro, Verse, Pre-Chorus, Chorus, Bridge, Solo, Outro
    val label: String = "[Verse 1]",
    val startTimeSec: Int = 0,
    val endTimeSec: Int = 30,
    val lines: List<String> = emptyList()
)

@Serializable
data class ChordEvent(
    val chord: String,
    val startTimeSec: Float,
    val endTimeSec: Float,
    val confidence: Float = 0.92f
)

@Serializable
data class GuitarOption(
    val id: String,
    val name: String, // Original, Easier, Open Chords, Minimal Barre Chords
    val soundingKey: String,
    val playedKeyShape: String,
    val capoFret: Int,
    val playedShapes: List<String>,
    val chordMapping: Map<String, String>,
    val difficulty: GuitarDifficulty,
    val barreChordCount: Int,
    val openChordCount: Int,
    val summary: String
)

@Serializable
data class ChordVoicing(
    val chordName: String,
    val variationName: String = "Standard Voicing",
    val frets: List<Int>,   // 6 strings [E, A, D, G, B, e]: -1 = muted, 0 = open, 1..4 = relative fret
    val fingers: List<Int>, // 0 = open/mute, 1 = Index, 2 = Middle, 3 = Ring, 4 = Pinky
    val baseFret: Int = 1,
    val stringNotes: List<String> = emptyList(),
    val playingTip: String = ""
)

@Serializable
data class SunoStyleAnalysis(
    val originalPrompt: String = "",
    val genre: String = "Indie Rock / Heartland",
    val subgenre: String = "Pub Rock / Alt-Country",
    val era: String = "Late 90s / Modern Studio",
    val instrumentation: String = "Overdriven Telecaster, Acoustic 12-String, Warm P-Bass, Tight Snare",
    val vocalCharacter: String = "Raw baritone lead, subtle rasp, double-tracked chorus harmonies",
    val tempoFeel: String = "118 BPM • Driving 4/4 eighth-note push",
    val dynamics: String = "Sparse palm-muted verses building to wide open-chord choruses",
    val productionCharacter: String = "Dry studio room ambience, tape saturation, punchy low-mids",
    val arrangement: String = "Guitar intro hook -> stripped Verse 1 -> Pre-Chorus lift -> full band Chorus",
    val songStructureTagSummary: String = "[Intro] [Verse 1] [Pre-Chorus] [Chorus] [Verse 2] [Bridge] [Guitar Solo] [Outro]",
    val conciseRecommendedPrompt: String = "Heartland indie pub-rock, 118 BPM driving 4/4, crunchy Telecaster rhythm, warm acoustic layer, raw expressive baritone vocal, dry analog studio punch"
)

@Serializable
data class LineDiagnostic(
    val lineIndex: Int,
    val sectionLabel: String,
    val text: String,
    val syllableCount: Int,
    val stressPattern: String,
    val rhymeEnding: String,
    val flags: List<String> = emptyList(), // e.g., "AI Cliché", "Syllable jump", "Strong hook"
    val suggestionNote: String = ""
)

@Serializable
data class LyricDiagnosisReport(
    val lyricStructureSummary: String = "",
    val rhymePatternSummary: String = "",
    val repeatedPhrases: List<String> = emptyList(),
    val cadenceConsistencyScore: Int = 88,
    val verseChorusDifferentiation: String = "",
    val detectedCliches: List<String> = emptyList(),
    val awkwardWordingNotes: List<String> = emptyList(),
    val povConsistency: String = "Consistent 1st-person perspective",
    val tenseConsistency: String = "Present / Reflective Past balanced",
    val emotionalProgression: String = "",
    val hookStrength: String = "Strong central chorus anchor",
    val sectionLengthNotes: String = "Balanced 4-line verses and 4-line chorus",
    val singabilityIssues: List<String> = emptyList(),
    val genreStyleCoherence: String = "High alignment with guitar arrangement",
    val lineDiagnostics: List<LineDiagnostic> = emptyList()
)

@Serializable
data class LineRewriteRequest(
    val originalLine: String,
    val sectionContext: String,
    val mode: String, // Humanise, Tighten, Keep cadence, Alternative line, Improve rhyme, Keep meaning, More conversational, More Australian, More country, More rock, Less cliché
    val keepCadence: Boolean,
    val targetSyllables: Int,
    val targetStressPattern: String
)

@Serializable
data class LineRewriteSuggestion(
    val text: String,
    val syllableCount: Int,
    val stressPattern: String,
    val rationale: String
)

@Serializable
data class SongAnalysisProject(
    val id: String,
    val title: String,
    val artistOrSource: String = "Uploaded Audio",
    val durationSec: Int = 195,
    val tempoBpm: Int = 118,
    val timeSignature: String = "4/4",
    val detectedKey: String = "Bb Major",
    val playableKey: String = "G Major",
    val recommendedCapo: Int = 3,
    val tuning: String = "Standard (E A D G B E) • A440",
    val feel: String = "Driving Acoustic / Studio Groove",
    val strummingPattern: String = "D - D U - U D U  (Accent 2 & 4)",
    val confidence: ConfidenceMetrics = ConfidenceMetrics(),
    val sections: List<SongSection> = emptyList(),
    val chordEvents: List<ChordEvent> = emptyList(),
    val chordsUsed: List<String> = emptyList(),
    val riffTab: String = "",
    val producerNotes: String = "",
    val sunoPrompt: String = "",
    val styleAnalysis: SunoStyleAnalysis = SunoStyleAnalysis(),
    val lyricAnalysis: LyricDiagnosisReport? = null,
    val audioUriOrPath: String? = null,
    val dspSummary: String = "",
    val isSampleProject: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
enum class StudioChatRole(
    val title: String,
    val badge: String,
    val systemPrompt: String
) {
    GUITAR_COACH(
        title = "Guitar & Capo Coach",
        badge = "Fretboard, Capo & Strumming Specialist",
        systemPrompt = "You are the Don't Fret Guitar & Capo Coach by DCI. Help the guitarist play the active song sheet effortlessly. Give practical fingerings, capo advice, strumming groove tips, bass walkdowns, and chord transition shortcuts. Keep answers concise, musical, and encouraging."
    ),
    SUNO_PRODUCER(
        title = "Suno 6 Arrangement Producer",
        badge = "Style Tags, Dynamics & Mix Architect",
        systemPrompt = "You are the Don't Fret Suno 6 Arrangement Producer by DCI. Help the user craft concise, musically meaningful Suno v6 style prompts, meta tags ([Intro], [Pre-Chorus], [Bridge], [Guitar Solo]), instrumentation pairings, and arrangement lifts without keyword soup."
    ),
    LYRIC_DOCTOR(
        title = "Cadence & Lyric Doctor",
        badge = "Meter, Syllable Lock & Cliché Editor",
        systemPrompt = "You are the Don't Fret Cadence & Lyric Doctor by DCI. Focus on syllable counts, stress patterns (da-DUM), natural phrasing, Australian/Heartland/Rock/Country authenticity, and removing generic AI clichés without overwriting the user's voice."
    )
}

@Serializable
data class StudioChatMessage(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val roleLabel: String = "",
    val modelUsed: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

