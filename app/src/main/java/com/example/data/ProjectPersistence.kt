package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.domain.MusicTheoryEngine
import com.example.model.ConfidenceMetrics
import com.example.model.SongAnalysisProject
import com.example.model.SongSection
import com.example.model.SunoStyleAnalysis
import com.example.network.GeminiService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(tableName = "song_projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artistOrSource: String,
    val detectedKey: String,
    val tempoBpm: Int,
    val isSampleProject: Boolean,
    val createdAt: Long,
    val payloadJson: String
)

@Dao
interface ProjectDao {
    @Query("SELECT * FROM song_projects ORDER BY createdAt DESC")
    fun observeAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT COUNT(*) FROM song_projects")
    suspend fun getProjectCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProject(entity: ProjectEntity)

    @Query("DELETE FROM song_projects WHERE id = :id")
    suspend fun deleteProjectById(id: String)
}

@Database(entities = [ProjectEntity::class], version = 1, exportSchema = false)
abstract class DontFretDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao

    companion object {
        @Volatile
        private var INSTANCE: DontFretDatabase? = null

        fun getInstance(context: Context): DontFretDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DontFretDatabase::class.java,
                    "dont_fret_studio.db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class ProjectRepository(private val dao: ProjectDao) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val allProjects: Flow<List<SongAnalysisProject>> = dao.observeAllProjects().map { list ->
        list.mapNotNull { entity ->
            try {
                json.decodeFromString<SongAnalysisProject>(entity.payloadJson)
            } catch (_: Exception) {
                null
            }
        }
    }

    suspend fun saveProject(project: SongAnalysisProject) {
        val entity = ProjectEntity(
            id = project.id,
            title = project.title,
            artistOrSource = project.artistOrSource,
            detectedKey = project.detectedKey,
            tempoBpm = project.tempoBpm,
            isSampleProject = project.isSampleProject,
            createdAt = project.createdAt,
            payloadJson = json.encodeToString(project)
        )
        dao.upsertProject(entity)
    }

    suspend fun deleteProject(id: String) {
        dao.deleteProjectById(id)
    }

    suspend fun ensureSeedProjects() {
        if (dao.getProjectCount() == 0) {
            buildStarterProjects().forEach { saveProject(it) }
        }
    }

    companion object {
        fun buildStarterProjects(): List<SongAnalysisProject> {
            // 1. Showcase Track matching the spec's exact Bb - Eb - F - Gm -> Capo 3 (G - C - D - Em) example!
            val sections1 = listOf(
                SongSection(
                    type = "Intro",
                    label = "[Intro]",
                    startTimeSec = 0,
                    endTimeSec = 16,
                    lines = listOf(
                        "| [Bb] / / / | [Eb] / / / | [F] / / / | [Gm] / [F] / |"
                    )
                ),
                SongSection(
                    type = "Verse",
                    label = "[Verse 1]",
                    startTimeSec = 16,
                    endTimeSec = 48,
                    lines = listOf(
                        "I heard your [Bb]voice this morning [Eb]coming through the radio,",
                        "Static on the [Gm]highway where the [F]river starts to slow,",
                        "Coffee on the [Bb]dashboard and the [Eb]sunlight in my eyes,",
                        "Counting every [Gm]mile post under [F]wide Australian skies."
                    )
                ),
                SongSection(
                    type = "Pre-Chorus",
                    label = "[Pre-Chorus]",
                    startTimeSec = 48,
                    endTimeSec = 68,
                    lines = listOf(
                        "And the [Eb]tyres keep hummin' a [F]steady refrain,",
                        "Washing the [Gm]dust off the [F]windowpane."
                    )
                ),
                SongSection(
                    type = "Chorus",
                    label = "[Chorus]",
                    startTimeSec = 68,
                    endTimeSec = 104,
                    lines = listOf(
                        "Don't [Bb]fret about the miles we left [Eb]behind us in the dark,",
                        "Every [F]broken string still knows the way to [Gm]strike another spark,",
                        "Put the [Bb]capo on the third fret let the [Eb]open chords ring true,",
                        "Every [Gm]road out past the horizon leads [F]straight back home to [Bb]you."
                    )
                ),
                SongSection(
                    type = "Verse",
                    label = "[Verse 2]",
                    startTimeSec = 104,
                    endTimeSec = 136,
                    lines = listOf(
                        "Stopped in at the [Bb]roadhouse where the [Eb]jukebox plays in flat,",
                        "Old bloke in the [Gm]corner tipped his [F]battered Akubra hat,",
                        "Said a [Bb]song don't need a million [Eb]chords to say what's real,",
                        "Just four [Gm]honest changes and the [F]way the timber feels."
                    )
                ),
                SongSection(
                    type = "Bridge",
                    label = "[Bridge]",
                    startTimeSec = 136,
                    endTimeSec = 164,
                    lines = listOf(
                        "Turn the [Cm]dial through the midnight [Eb]rain,",
                        "Let the [Bb]valve amp warm up the [F]melody again,",
                        "From the [Gm]backseat case to the [Eb]front porch light,",
                        "We're [F]locked right in the pocket tonight."
                    )
                ),
                SongSection(
                    type = "Outro",
                    label = "[Outro]",
                    startTimeSec = 164,
                    endTimeSec = 192,
                    lines = listOf(
                        "| [Bb] / / / | [Eb] / / / | [Gm] / [F] / | [Bb] (Let ring) |"
                    )
                )
            )
            val lyrics1 = sections1.joinToString("\n") { "${it.label}\n${it.lines.joinToString("\n")}" }

            val track1 = SongAnalysisProject(
                id = "sample_southern_highway_bb",
                title = "Southern Highway Signal",
                artistOrSource = "Sample Studio Reference • Bb Major (Capo 3 → G)",
                durationSec = 192,
                tempoBpm = 118,
                timeSignature = "4/4",
                detectedKey = "Bb Major",
                playableKey = "G Major",
                recommendedCapo = 3,
                tuning = "Standard (E A D G B E) • +0c A440",
                feel = "Driving Heartland Pub-Rock / 8th-Note Push",
                strummingPattern = "D - D U - U D U  [Accent 2 & 4]",
                confidence = ConfidenceMetrics(
                    overallPercent = 96,
                    keyConfidencePercent = 98,
                    tempoConfidencePercent = 95,
                    timingLockLabel = "Sample Reference"
                ),
                sections = sections1,
                chordEvents = GeminiService.buildTimelineChordEvents(sections1, 192),
                chordsUsed = listOf("Bb", "Eb", "F", "Gm", "Cm"),
                riffTab = """
                    [Capo 3 — Played in G Shapes (Sounding Key: Bb Major)]
                    e|-------3-----------0-----------2-----------0-------|
                    B|-----0---0-------1---1-------3---3-------0---0-----|
                    G|---0-------0---0-------0---2-------2---0-------0---|
                    D|-------------------------0-----------2-------------|
                    A|-------------3-------------------------------------|
                    E|-3-------------------------------------------------|
                """.trimIndent(),
                producerNotes = "Concert pitch is Bb Major (Bb – Eb – F – Gm). Placing a Capo on Fret 3 transforms the entire track into effortless open G – C – D – Em shapes while keeping the exact Bb Major sounding key!",
                sunoPrompt = "[Style: Heartland Australian pub-rock, 118 BPM driving 4/4, acoustic dreadnought + crunchy Telecaster, warm P-bass, raw baritone vocal, dry studio punch]\n\n$lyrics1",
                styleAnalysis = SunoStyleAnalysis(
                    originalPrompt = "Heartland Australian indie rock, 118 BPM, acoustic and Telecaster guitars, raw baritone vocal",
                    genre = "Heartland Rock / Indie",
                    subgenre = "Australian Pub-Rock / Americana",
                    era = "Modern Analog Studio",
                    instrumentation = "Maton Acoustic (Capo 3), Overdriven Telecaster, P-Bass, Tight Live Kit",
                    vocalCharacter = "Warm baritone lead with conversational grit and wide chorus lift",
                    tempoFeel = "118 BPM • Driving 4/4 highway pulse",
                    dynamics = "Palm-muted verse pocket opening into full-strumming chorus",
                    productionCharacter = "Dry room mics, subtle tape warmth, crisp guitar separation",
                    arrangement = "4-bar intro hook -> Verse 1 -> Pre-Chorus -> Chorus -> Verse 2 -> Bridge -> Outro",
                    songStructureTagSummary = "[Intro] [Verse 1] [Pre-Chorus] [Chorus] [Verse 2] [Bridge] [Outro]",
                    conciseRecommendedPrompt = "Heartland Australian pub-rock, 118 BPM driving 4/4, bright acoustic + crunchy Telecaster rhythm, warm P-bass, expressive baritone vocal, punchy live studio mix"
                ),
                lyricAnalysis = MusicTheoryEngine.analyzeLyricsDeterministically(lyrics1),
                dspSummary = "Dominant Notes: [Bb, F, Eb, G] | Tuning Offset: +0 cents from A440 | Est. Tempo: 118 BPM | 2s Root Timeline: Bb → Eb → Gm → F",
                isSampleProject = true,
                createdAt = System.currentTimeMillis() - 3600_000L
            )

            // 2. Mildura Midnight Drive (from user's HTML demo prompt in E Minor)
            val sections2 = listOf(
                SongSection(
                    type = "Intro",
                    label = "[Intro]",
                    startTimeSec = 0,
                    endTimeSec = 14,
                    lines = listOf("| [Em] / / / | [Am7] / / / | [B7] / / / | [Em] / / / |")
                ),
                SongSection(
                    type = "Verse",
                    label = "[Verse 1]",
                    startTimeSec = 14,
                    endTimeSec = 50,
                    lines = listOf(
                        "Red dirt [Em]blowing cross the Calder [Am7]line past midnight,",
                        "High beams [D7]cutting through the citrus [G]groves in white,",
                        "Got a [C]hollowbody Gretsch in the [Am]back of the ute,",
                        "And a [B7]pocketful of silver and a [Em]worn-out boot."
                    )
                ),
                SongSection(
                    type = "Chorus",
                    label = "[Chorus]",
                    startTimeSec = 50,
                    endTimeSec = 88,
                    lines = listOf(
                        "Rolling through [Am7]Mildura on a [D7]west-coast swing,",
                        "Hear that [G]slapback echo make the [Cmaj7]steel strings sing,",
                        "When the [Am]Murray River mist starts [B7]rising off the bend,",
                        "We'll ride that [Em]twangy backbeat till the [B7]highway [Em]ends."
                    )
                )
            )
            val lyrics2 = sections2.joinToString("\n") { "${it.label}\n${it.lines.joinToString("\n")}" }

            val track2 = SongAnalysisProject(
                id = "sample_mildura_midnight_em",
                title = "Mildura Midnight Swing",
                artistOrSource = "Sample Studio Reference • E Minor (Open Guitar)",
                durationSec = 156,
                tempoBpm = 126,
                timeSignature = "4/4",
                detectedKey = "E Minor",
                playableKey = "E Minor",
                recommendedCapo = 0,
                tuning = "Standard (E A D G B E) • -2c A440",
                feel = "Gritty West Coast Swing / Rockabilly Boom-Chicka",
                strummingPattern = "Bass - D U - Bass - D U  (Swing 8ths)",
                confidence = ConfidenceMetrics(95, 97, 94, "Sample Reference"),
                sections = sections2,
                chordEvents = GeminiService.buildTimelineChordEvents(sections2, 156),
                chordsUsed = listOf("Em", "Am7", "D7", "G", "C", "Am", "B7", "Cmaj7"),
                riffTab = """
                    [Open E Minor Bass Walkdown]
                    e|-------------------------------------------|
                    B|-------------------------------------------|
                    G|-------------------------------------------|
                    D|-------------------------1---2-------------|
                    A|---------0---2---3---2-----------2---------|
                    E|-0---3-------------------------------0-----|
                """.trimIndent(),
                producerNotes = "Natural open E minor guitar key—no capo required. Alternate thumb bass notes on beats 1 & 3 for authentic boom-chicka rockabilly pocket.",
                sunoPrompt = "[Style: Gritty West Coast swing rockabilly, 126 BPM shuffle in E minor, hollowbody Gretsch slapback delay, upright bass slap, smoky baritone vocal]\n\n$lyrics2",
                lyricAnalysis = MusicTheoryEngine.analyzeLyricsDeterministically(lyrics2),
                dspSummary = "Dominant Notes: [E, B, A, G] | Tuning Offset: -2 cents from A440 | Est. Tempo: 126 BPM | 2s Root Timeline: Em → Am7 → D7 → G",
                isSampleProject = true,
                createdAt = System.currentTimeMillis() - 7200_000L
            )

            return listOf(track1, track2)
        }
    }
}
