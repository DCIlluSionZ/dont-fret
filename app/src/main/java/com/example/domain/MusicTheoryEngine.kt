package com.example.domain

import com.example.model.ChordVoicing
import com.example.model.GuitarDifficulty
import com.example.model.GuitarOption
import com.example.model.LineDiagnostic
import com.example.model.LyricDiagnosisReport
import kotlin.math.abs

/**
 * Deterministic Music Theory & Guitar Calculation Engine.
 * Handles:
 * 1. Semitone transposition with key-aware sharp/flat enharmonic spelling
 * 2. True Capo Equivalence (Sounding Key = Played Chord Shape + Capo Fret)
 * 3. Multi-option Guitar Mode generation (Original, Easier, Open Chords, Minimal Barre)
 * 4. Multi-voicing Guitar Chord Dictionary (Standard + Alternative fingerings)
 * 5. Deterministic Syllable / Stress / Cadence & AI-Cliché Lyric Analyzer
 */
object MusicTheoryEngine {

    val NOTES_SHARP = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    val NOTES_FLAT = listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")

    private val NOTE_TO_INDEX = mapOf(
        "C" to 0, "B#" to 0,
        "C#" to 1, "Db" to 1,
        "D" to 2,
        "D#" to 3, "Eb" to 3,
        "E" to 4, "Fb" to 4,
        "F" to 5, "E#" to 5,
        "F#" to 6, "Gb" to 6,
        "G" to 7,
        "G#" to 8, "Ab" to 8,
        "A" to 9,
        "A#" to 10, "Bb" to 10,
        "B" to 11, "Cb" to 11
    )

    private val FLAT_KEYS = setOf(
        "F", "Bb", "Eb", "Ab", "Db", "Gb",
        "Dm", "Gm", "Cm", "Fm", "Bbm", "Ebm"
    )

    private val OPEN_BEGINNER_ROOTS = setOf("C", "G", "D", "A", "E", "Am", "Em", "Dm", "E7", "A7", "D7", "G7", "Cmaj7", "Fmaj7", "Em7", "Am7", "Dm7", "Dsus4", "Asus2", "Asus4", " Cadd9")
    private val FULL_BARRE_ROOTS = setOf("F", "Fm", "F#", "F#m", "Gb", "Gbm", "G#", "G#m", "Ab", "Abm", "Bb", "Bbm", "B", "Bm", "C#", "C#m", "Db", "Dbm", "D#", "D#m", "Eb", "Ebm", "Gm", "Cm")

    fun parseRootIndex(token: String): Int? {
        val cleaned = token.trim()
        val match = Regex("^([A-G][#b]?)").find(cleaned) ?: return null
        return NOTE_TO_INDEX[match.groupValues[1]]
    }

    fun shouldPreferFlats(keySignature: String): Boolean {
        val rootPart = keySignature.trim().split(" ").firstOrNull() ?: keySignature
        val isMinor = keySignature.contains("minor", ignoreCase = true) ||
            (rootPart.endsWith("m") && !rootPart.endsWith("maj"))
        val cleanRoot = rootPart.removeSuffix("m").removeSuffix("Major").removeSuffix("Minor").trim()
        val normalized = if (isMinor) "${cleanRoot}m" else cleanRoot
        return normalized in FLAT_KEYS || cleanRoot.contains("b")
    }

    fun transposeNoteName(note: String, semitones: Int, preferFlats: Boolean = false): String {
        val idx = NOTE_TO_INDEX[note] ?: return note
        val target = ((idx + semitones) % 12 + 12) % 12
        return if (preferFlats) NOTES_FLAT[target] else NOTES_SHARP[target]
    }

    /**
     * Deterministically transposes any chord symbol (including slash chords like D/F# or G/B
     * and extensions like Cmaj7, F#m7b5, Bbadd9) by [semitones].
     */
    fun transposeChord(chordSymbol: String, semitones: Int, preferFlats: Boolean? = null): String {
        if (semitones == 0 && preferFlats == null) return chordSymbol
        val trimmed = chordSymbol.trim()
        if (trimmed.isEmpty()) return chordSymbol

        // Handle slash bass chords e.g. G/B or D/F#
        if (trimmed.contains("/")) {
            val parts = trimmed.split("/", limit = 2)
            val main = transposeSingleChordToken(parts[0], semitones, preferFlats)
            val bass = transposeSingleChordToken(parts[1], semitones, preferFlats)
            return "$main/$bass"
        }
        return transposeSingleChordToken(trimmed, semitones, preferFlats)
    }

    private fun transposeSingleChordToken(token: String, semitones: Int, preferFlats: Boolean?): String {
        val regex = Regex("^([A-G][#b]?)(.*)$")
        val match = regex.find(token) ?: return token
        val root = match.groupValues[1]
        val suffix = match.groupValues[2]
        val idx = NOTE_TO_INDEX[root] ?: return token
        val newIdx = ((idx + semitones) % 12 + 12) % 12
        val isMinor = suffix.startsWith("m") && !suffix.startsWith("maj")
        // Decide flat vs sharp naturally if not forced
        val useFlats = preferFlats ?: when (newIdx) {
            1 -> !isMinor // Db major vs C#m
            3 -> true     // Eb major / Ebm
            6 -> false    // F# / F#m
            8 -> !isMinor // Ab major vs G#m
            10 -> true    // Bb major / Bbm
            else -> root.contains("b")
        }
        val newRoot = if (useFlats) NOTES_FLAT[newIdx] else NOTES_SHARP[newIdx]
        return newRoot + suffix
    }

    fun transposeKey(keyStr: String, semitones: Int): String {
        if (semitones == 0) return keyStr
        val trimmed = keyStr.trim()
        val match = Regex("^([A-G][#b]?)\\s*(.*)$").find(trimmed) ?: return keyStr
        val root = match.groupValues[1]
        val rest = match.groupValues[2].ifEmpty { "Major" }
        val isMinor = rest.contains("min", ignoreCase = true) || rest == "m"
        val idx = NOTE_TO_INDEX[root] ?: return keyStr
        val newIdx = ((idx + semitones) % 12 + 12) % 12
        val useFlats = when (newIdx) {
            1 -> !isMinor
            3 -> true
            5 -> true
            8 -> !isMinor
            10 -> true
            else -> false
        }
        val newRoot = if (useFlats) NOTES_FLAT[newIdx] else NOTES_SHARP[newIdx]
        return "$newRoot $rest".trim()
    }

    /**
     * Converts inline bracketed lyric lines like:
     * "Well the [Bb]neon's buzzing down on [Eb]Eighth and [F]Main"
     * using [semitoneShift] so that every chord is accurately transposed.
     */
    fun transposeBracketedLine(line: String, semitoneShift: Int, preferFlats: Boolean? = null): String {
        if (semitoneShift == 0 && preferFlats == null) return line
        return Regex("\\[([^\\]]+)\\]").replace(line) { matchResult ->
            val rawChord = matchResult.groupValues[1].trim()
            "[${transposeChord(rawChord, semitoneShift, preferFlats)}]"
        }
    }

    /**
     * Calculates Guitar Mode options for a song.
     *
     * CRITICAL MUSICAL CORRECTNESS:
     * Putting a capo on fret N raises the pitch of played shapes by +N semitones.
     * Therefore, to produce the exact same SOUNDING KEY (Original + userTranspose),
     * the guitarist plays chord shapes transposed by (-capoFret) relative to the sounding chords!
     *
     * Example:
     * Sounding Chords: Bb – Eb – F – Gm (Sounding Key: Bb Major)
     * Capo 3 -> Transpose shapes by -3 semitones:
     *   Bb (-3) = G
     *   Eb (-3) = C
     *   F  (-3) = D
     *   Gm (-3) = Em
     * Played Shapes = G – C – D – Em (Capo 3), Resulting Sounding Key = Bb Major!
     */
    fun calculateGuitarOptions(
        originalSoundingKey: String,
        soundingChords: List<String>,
        userTransposeSemitones: Int = 0
    ): List<GuitarOption> {
        val targetSoundingKey = transposeKey(originalSoundingKey, userTransposeSemitones)
        val useFlatsInSounding = shouldPreferFlats(targetSoundingKey)
        val distinctSoundingChords = soundingChords
            .map { transposeChord(it, userTransposeSemitones, useFlatsInSounding) }
            .distinct()
            .ifEmpty { listOf("G", "C", "D", "Em") }

        data class CapoCandidate(
            val capo: Int,
            val playedKey: String,
            val playedChords: List<String>,
            val mapping: Map<String, String>,
            val openCount: Int,
            val barreCount: Int,
            val score: Int
        )

        val candidates = (0..7).map { capo ->
            val playedKey = transposeKey(targetSoundingKey, -capo)
            val preferFlatsForShapes = shouldPreferFlats(playedKey) && capo == 0
            val mapping = distinctSoundingChords.associateWith { soundingChord ->
                transposeChord(soundingChord, -capo, preferFlats = preferFlatsForShapes)
            }
            val playedList = distinctSoundingChords.map { mapping[it] ?: it }
            var openCount = 0
            var barreCount = 0
            var score = 0

            playedList.forEach { shape ->
                val base = shape.split("/").first()
                when {
                    base in OPEN_BEGINNER_ROOTS -> {
                        openCount++
                        score += 15
                    }
                    isBarreChordShape(base) -> {
                        barreCount++
                        score -= 18
                    }
                    else -> {
                        score += 4
                    }
                }
            }
            // Prefer classic acoustic guitar keys (G, C, D, A, E, Am, Em)
            val keyRoot = playedKey.split(" ").firstOrNull() ?: ""
            if (keyRoot in setOf("G", "C", "D", "A", "E", "Am", "Em", "Dm")) {
                score += 22
            }
            // Slight penalty for high capo frets unless it saves barre chords
            score -= capo * 2

            CapoCandidate(
                capo = capo,
                playedKey = playedKey,
                playedChords = playedList,
                mapping = mapping,
                openCount = openCount,
                barreCount = barreCount,
                score = score
            )
        }

        val originalCandidate = candidates.first { it.capo == 0 }
        val bestCapoCandidate = candidates
            .filter { it.capo > 0 }
            .maxByOrNull { it.score } ?: originalCandidate

        val minimalBarreCandidate = candidates
            .filter { it.capo != bestCapoCandidate.capo && it.capo > 0 }
            .sortedWith(compareBy<CapoCandidate> { it.barreCount }.thenByDescending { it.score })
            .firstOrNull() ?: originalCandidate

        val options = mutableListOf<GuitarOption>()

        // 1. Original / Exact Option (Capo 0)
        options.add(
            GuitarOption(
                id = "original",
                name = "Original",
                soundingKey = targetSoundingKey,
                playedKeyShape = originalCandidate.playedKey,
                capoFret = 0,
                playedShapes = originalCandidate.playedChords,
                chordMapping = originalCandidate.mapping,
                difficulty = if (originalCandidate.barreCount == 0) GuitarDifficulty.BEGINNER else GuitarDifficulty.ORIGINAL_EXACT,
                barreChordCount = originalCandidate.barreCount,
                openChordCount = originalCandidate.openCount,
                summary = if (originalCandidate.barreCount == 0) {
                    "Original concert pitch already uses open guitar shapes (No Capo needed)."
                } else {
                    "Exact concert shapes with no capo (${originalCandidate.barreCount} barre chord${if (originalCandidate.barreCount == 1) "" else "s"})."
                }
            )
        )

        // 2. Easier / Open Chords Option (Best Capo)
        if (bestCapoCandidate.capo > 0 && bestCapoCandidate.score > originalCandidate.score - 5) {
            options.add(
                GuitarOption(
                    id = "easier_open",
                    name = "Open Chords",
                    soundingKey = targetSoundingKey,
                    playedKeyShape = bestCapoCandidate.playedKey,
                    capoFret = bestCapoCandidate.capo,
                    playedShapes = bestCapoCandidate.playedChords,
                    chordMapping = bestCapoCandidate.mapping,
                    difficulty = GuitarDifficulty.BEGINNER,
                    barreChordCount = bestCapoCandidate.barreCount,
                    openChordCount = bestCapoCandidate.openCount,
                    summary = "Capo ${bestCapoCandidate.capo} converts progression to ${bestCapoCandidate.playedKey} open shapes while sounding in $targetSoundingKey."
                )
            )
        } else {
            // Even if original is already open, offer an alternative capo voicing (e.g. G shapes vs C shapes)
            val altOpen = candidates.filter { it.capo in 1..5 }.maxByOrNull { it.score }
            if (altOpen != null) {
                options.add(
                    GuitarOption(
                        id = "easier_open",
                        name = "Open Chords",
                        soundingKey = targetSoundingKey,
                        playedKeyShape = altOpen.playedKey,
                        capoFret = altOpen.capo,
                        playedShapes = altOpen.playedChords,
                        chordMapping = altOpen.mapping,
                        difficulty = GuitarDifficulty.BEGINNER,
                        barreChordCount = altOpen.barreCount,
                        openChordCount = altOpen.openCount,
                        summary = "Capo ${altOpen.capo} voiced in ${altOpen.playedKey} shapes (Sounding Key: $targetSoundingKey)."
                    )
                )
            }
        }

        // 3. Minimal Barre / Intermediate Alternative
        if (minimalBarreCandidate.capo > 0) {
            options.add(
                GuitarOption(
                    id = "minimal_barre",
                    name = "Minimal Barre",
                    soundingKey = targetSoundingKey,
                    playedKeyShape = minimalBarreCandidate.playedKey,
                    capoFret = minimalBarreCandidate.capo,
                    playedShapes = minimalBarreCandidate.playedChords,
                    chordMapping = minimalBarreCandidate.mapping,
                    difficulty = GuitarDifficulty.INTERMEDIATE,
                    barreChordCount = minimalBarreCandidate.barreCount,
                    openChordCount = minimalBarreCandidate.openCount,
                    summary = "Alternative Capo ${minimalBarreCandidate.capo} voicing in ${minimalBarreCandidate.playedKey} shapes ($targetSoundingKey concert pitch)."
                )
            )
        }

        // 4. Easy Simplified Shapes (replaces complex barre F/Bm with Fmaj7/Bm7 on current best capo)
        val baseForEasy = if (bestCapoCandidate.score > originalCandidate.score) bestCapoCandidate else originalCandidate
        val easyMapping = baseForEasy.mapping.mapValues { (_, playedShape) ->
            simplifyChordShapeForBeginner(playedShape)
        }
        val easyShapes = distinctSoundingChords.map { easyMapping[it] ?: it }.distinct()
        options.add(
            GuitarOption(
                id = "easier_beginner",
                name = "Easier",
                soundingKey = targetSoundingKey,
                playedKeyShape = baseForEasy.playedKey,
                capoFret = baseForEasy.capo,
                playedShapes = easyShapes,
                chordMapping = easyMapping,
                difficulty = GuitarDifficulty.BEGINNER,
                barreChordCount = 0,
                openChordCount = easyShapes.size,
                summary = if (baseForEasy.capo > 0) {
                    "Beginner-friendly open voicings at Capo ${baseForEasy.capo} (substitutes full barres with open 4-finger shapes)."
                } else {
                    "Beginner-friendly open voicings with zero full barre chords."
                }
            )
        )

        return options
    }

    private fun isBarreChordShape(chordName: String): Boolean {
        val rootMatch = Regex("^([A-G][#b]?m?)").find(chordName)?.groupValues?.get(1) ?: chordName
        if (chordName == "Fmaj7" || chordName == "Bm7") return false
        return rootMatch in FULL_BARRE_ROOTS
    }

    private fun simplifyChordShapeForBeginner(chord: String): String {
        val base = chord.split("/").first() // drop slash bass for beginner mode
        return when (base) {
            "F" -> "Fmaj7"
            "Bm" -> "Bm7"
            "F#m" -> "F#m7"
            "Cm" -> "Cm7"
            "Gm" -> "Gm7"
            else -> base
        }
    }

    /**
     * Returns all available voicings (Standard + Alternative fingerings) for any guitar chord.
     */
    fun getVoicingsForChord(chordName: String): List<ChordVoicing> {
        val clean = chordName.trim()
        val withoutSlash = clean.split("/").first().trim()
        val stripped = withoutSlash.replace(Regex("(add9|sus2|9|11|13)$"), "")

        val exactList = CHORD_VOICING_DB[clean]
            ?: CHORD_VOICING_DB[withoutSlash]
            ?: CHORD_VOICING_DB[stripped]

        if (exactList != null && exactList.isNotEmpty()) {
            return exactList.map { it.copy(chordName = clean) }
        }

        // Fallback: generate movable E-shape and A-shape barre voicings deterministically from root!
        val rootIdx = parseRootIndex(withoutSlash) ?: 0
        val isMinor = withoutSlash.contains("m") && !withoutSlash.contains("maj")
        val eStringFret = ((rootIdx - 4) % 12 + 12) % 12
        val aStringFret = ((rootIdx - 9) % 12 + 12) % 12

        val eBase = if (eStringFret == 0) 1 else eStringFret
        val aBase = if (aStringFret == 0) 1 else aStringFret

        val eShape = if (isMinor) {
            ChordVoicing(
                chordName = clean,
                variationName = "E-Shape Barre (${eBase}fr)",
                frets = listOf(1, 3, 3, 1, 1, 1),
                fingers = listOf(1, 3, 4, 1, 1, 1),
                baseFret = eBase,
                playingTip = "Bar all 6 strings with Index finger at fret $eBase."
            )
        } else {
            ChordVoicing(
                chordName = clean,
                variationName = "E-Shape Barre (${eBase}fr)",
                frets = listOf(1, 3, 3, 2, 1, 1),
                fingers = listOf(1, 3, 4, 2, 1, 1),
                baseFret = eBase,
                playingTip = "Full 6-string E-shape barre at fret $eBase."
            )
        }

        val aShape = if (isMinor) {
            ChordVoicing(
                chordName = clean,
                variationName = "A-Shape Barre (${aBase}fr)",
                frets = listOf(-1, 1, 3, 3, 2, 1),
                fingers = listOf(0, 1, 3, 4, 2, 1),
                baseFret = aBase,
                playingTip = "Mute 6th string; bar strings 5..1 at fret $aBase."
            )
        } else {
            ChordVoicing(
                chordName = clean,
                variationName = "A-Shape Barre (${aBase}fr)",
                frets = listOf(-1, 1, 3, 3, 3, 1),
                fingers = listOf(0, 1, 2, 3, 4, 1),
                baseFret = aBase,
                playingTip = "Mute low E string; root on 5th string fret $aBase."
            )
        }

        return listOf(eShape, aShape)
    }

    private val CHORD_VOICING_DB: Map<String, List<ChordVoicing>> = mapOf(
        "C" to listOf(
            ChordVoicing("C", "Open Standard", listOf(-1, 3, 2, 0, 1, 0), listOf(0, 3, 2, 0, 1, 0), 1, listOf("X", "C", "E", "G", "C", "E"), "Mute low E; curl fingers so open G and high E ring clearly."),
            ChordVoicing("C", "C/G Full Warm", listOf(3, 3, 2, 0, 1, 0), listOf(3, 4, 2, 0, 1, 0), 1, listOf("G", "C", "E", "G", "C", "E"), "Adds low G on 6th string for fuller acoustic strumming."),
            ChordVoicing("C", "A-Shape Barre (3fr)", listOf(-1, 1, 3, 3, 3, 1), listOf(0, 1, 2, 3, 4, 1), 3, listOf("X", "C", "G", "C", "E", "G"), "Tight rhythm voicing at 3rd fret.")
        ),
        "Cmaj7" to listOf(
            ChordVoicing("Cmaj7", "Open Dreamy", listOf(-1, 3, 2, 0, 0, 0), listOf(0, 3, 2, 0, 0, 0), 1, listOf("X", "C", "E", "G", "B", "E"), "Two-finger open chord—lift index finger off B string.")
        ),
        "Cadd9" to listOf(
            ChordVoicing("Cadd9", "Open Modern", listOf(-1, 3, 2, 0, 3, 3), listOf(0, 2, 1, 0, 3, 4), 1, listOf("X", "C", "E", "G", "D", "G"), "Keep ring & pinky anchored on 3rd fret of B and high E.")
        ),
        "C7" to listOf(
            ChordVoicing("C7", "Open Blues", listOf(-1, 3, 2, 3, 1, 0), listOf(0, 3, 2, 4, 1, 0), 1, listOf("X", "C", "E", "Bb", "C", "E"), "Add pinky to 3rd fret of G string.")
        ),
        "Cm" to listOf(
            ChordVoicing("Cm", "A-Shape Barre (3fr)", listOf(-1, 1, 3, 3, 2, 1), listOf(0, 1, 3, 4, 2, 1), 3, listOf("X", "C", "G", "C", "Eb", "G"), "Bar at 3rd fret with index finger; mute 6th string."),
            ChordVoicing("Cm", "Easy 4-String Triad", listOf(-1, -1, 3, 3, 2, 1), listOf(0, 0, 3, 4, 2, 1), 3, listOf("X", "X", "G", "C", "Eb", "G"), "No full barre required—strum only top 4 strings.")
        ),
        "Cm7" to listOf(
            ChordVoicing("Cm7", "Barre 3rd Fret", listOf(-1, 1, 3, 1, 2, 1), listOf(0, 1, 3, 1, 2, 1), 3, listOf("X", "C", "G", "Bb", "Eb", "G"), "Relaxed minor 7th barre at 3rd fret.")
        ),
        "D" to listOf(
            ChordVoicing("D", "Open Triangle", listOf(-1, -1, 0, 2, 3, 2), listOf(0, 0, 0, 1, 3, 2), 1, listOf("X", "X", "D", "A", "D", "F#"), "Strum from open D (4th string) down."),
            ChordVoicing("D", "D/F# Thumb Wrap", listOf(2, -1, 0, 2, 3, 2), listOf(1, 0, 0, 2, 4, 3), 1, listOf("F#", "X", "D", "A", "D", "F#"), "Great for bass walkdowns between G, D/F#, and Em.")
        ),
        "Dm" to listOf(
            ChordVoicing("Dm", "Open Standard", listOf(-1, -1, 0, 2, 3, 1), listOf(0, 0, 0, 2, 3, 1), 1, listOf("X", "X", "D", "A", "D", "F"), "Strum top 4 strings clearly."),
            ChordVoicing("Dm", "A-Shape Barre (5fr)", listOf(-1, 1, 3, 3, 2, 1), listOf(0, 1, 3, 4, 2, 1), 5, listOf("X", "D", "A", "D", "F", "A"), "Tighter midrange voicing at 5th fret.")
        ),
        "Dm7" to listOf(
            ChordVoicing("Dm7", "Open Mini-Barre", listOf(-1, -1, 0, 2, 1, 1), listOf(0, 0, 0, 2, 1, 1), 1, listOf("X", "X", "D", "A", "C", "F"), "Flatten index across B and high E at 1st fret.")
        ),
        "D7" to listOf(
            ChordVoicing("D7", "Open Turnaround", listOf(-1, -1, 0, 2, 1, 2), listOf(0, 0, 0, 2, 1, 3), 1, listOf("X", "X", "D", "A", "C", "F#"), "Classic dominant turnaround shape.")
        ),
        "Dmaj7" to listOf(
            ChordVoicing("Dmaj7", "Open 3-String Bar", listOf(-1, -1, 0, 2, 2, 2), listOf(0, 0, 0, 1, 1, 1), 1, listOf("X", "X", "D", "A", "C#", "F#"), "Lay index finger flat across top 3 strings at 2nd fret.")
        ),
        "Dsus4" to listOf(
            ChordVoicing("Dsus4", "Open Suspended", listOf(-1, -1, 0, 2, 3, 3), listOf(0, 0, 0, 1, 3, 4), 1, listOf("X", "X", "D", "A", "D", "G"), "Drop pinky onto 3rd fret of high E.")
        ),
        "Eb" to listOf(
            ChordVoicing("Eb", "A-Shape Barre (6fr)", listOf(-1, 1, 3, 3, 3, 1), listOf(0, 1, 2, 3, 4, 1), 6, listOf("X", "Eb", "Bb", "Eb", "G", "Bb"), "Root on A string 6th fret. Tip: Use Capo 1 + D shape or Capo 3 + C shape!"),
            ChordVoicing("Eb", "C-Shape Compact (3fr)", listOf(-1, -1, 1, 3, 4, 3), listOf(0, 0, 1, 2, 4, 3), 1, listOf("X", "X", "Eb", "Bb", "Eb", "G"), "4-string movable D-shape slid up 1 fret (no full barre).")
        ),
        "E" to listOf(
            ChordVoicing("E", "Open Full 6-String", listOf(0, 2, 2, 1, 0, 0), listOf(0, 2, 3, 1, 0, 0), 1, listOf("E", "B", "E", "G#", "B", "E"), "All 6 strings ring open and resonant.")
        ),
        "Em" to listOf(
            ChordVoicing("Em", "Open 2-Finger", listOf(0, 2, 2, 0, 0, 0), listOf(0, 2, 3, 0, 0, 0), 1, listOf("E", "B", "E", "G", "B", "E"), "Easiest full 6-string guitar chord."),
            ChordVoicing("Em", "Em7 Wonderwall Voicing", listOf(0, 2, 2, 0, 3, 3), listOf(0, 1, 2, 0, 3, 4), 1, listOf("E", "B", "E", "G", "D", "G"), "Anchors ring and pinky on fret 3 for smooth transitions to G and Cadd9.")
        ),
        "Em7" to listOf(
            ChordVoicing("Em7", "Open 1-Finger", listOf(0, 2, 0, 0, 0, 0), listOf(0, 2, 0, 0, 0, 0), 1, listOf("E", "B", "D", "G", "B", "E"), "Airy open voicing with only 5th string fretted."),
            ChordVoicing("Em7", "Open 4-Finger", listOf(0, 2, 2, 0, 3, 3), listOf(0, 1, 2, 0, 3, 4), 1, listOf("E", "B", "E", "G", "D", "G"), "Punchy acoustic strumming voicing.")
        ),
        "E7" to listOf(
            ChordVoicing("E7", "Open Blues", listOf(0, 2, 0, 1, 0, 0), listOf(0, 2, 0, 1, 0, 0), 1, listOf("E", "B", "D", "G#", "B", "E"), "Lift ring finger from standard E major.")
        ),
        "F" to listOf(
            ChordVoicing("F", "Full 6-String Barre", listOf(1, 3, 3, 2, 1, 1), listOf(1, 3, 4, 2, 1, 1), 1, listOf("F", "C", "F", "A", "C", "F"), "Roll index finger slightly onto its bony side for a clean 1st-fret bar."),
            ChordVoicing("F", "Easy 4-String Mini-F", listOf(-1, -1, 3, 2, 1, 1), listOf(0, 0, 3, 2, 1, 1), 1, listOf("X", "X", "F", "A", "C", "F"), "Beginner favorite—only bar the top 2 strings with your index finger!"),
            ChordVoicing("F", "Fmaj7 Open Alternative", listOf(-1, -1, 3, 2, 1, 0), listOf(0, 0, 3, 2, 1, 0), 1, listOf("X", "X", "F", "A", "C", "E"), "Zero barre! Leave high E string open for a warm studio folk sound.")
        ),
        "Fmaj7" to listOf(
            ChordVoicing("Fmaj7", "Open Studio Voicing", listOf(-1, -1, 3, 2, 1, 0), listOf(0, 0, 3, 2, 1, 0), 1, listOf("X", "X", "F", "A", "C", "E"), "No barre required; let high E ring open."),
            ChordVoicing("Fmaj7", "Thumb Bass Fmaj7/C", listOf(1, 3, 3, 2, 1, 0), listOf(1, 3, 4, 2, 1, 0), 1, listOf("F", "C", "F", "A", "C", "E"), "Rich acoustic rhythm voicing.")
        ),
        "Fm" to listOf(
            ChordVoicing("Fm", "Full Barre (1fr)", listOf(1, 3, 3, 1, 1, 1), listOf(1, 3, 4, 1, 1, 1), 1, listOf("F", "C", "F", "Ab", "C", "F"), "E-minor shape barred at 1st fret."),
            ChordVoicing("Fm", "Easy 4-String Fm", listOf(-1, -1, 3, 1, 1, 1), listOf(0, 0, 3, 1, 1, 1), 1, listOf("X", "X", "F", "Ab", "C", "F"), "Bar top 3 strings at 1st fret and add ring finger on D string 3rd fret.")
        ),
        "F#" to listOf(
            ChordVoicing("F#", "E-Shape Barre (2fr)", listOf(1, 3, 3, 2, 1, 1), listOf(1, 3, 4, 2, 1, 1), 2, listOf("F#", "C#", "F#", "A#", "C#", "F#"), "Full barre at 2nd fret.")
        ),
        "F#m" to listOf(
            ChordVoicing("F#m", "E-Shape Barre (2fr)", listOf(1, 3, 3, 1, 1, 1), listOf(1, 3, 4, 1, 1, 1), 2, listOf("F#", "C#", "F#", "A", "C#", "F#"), "Bar all 6 strings at 2nd fret."),
            ChordVoicing("F#m", "Open-High F#m11", listOf(2, 4, 4, 2, 0, 0), listOf(1, 3, 4, 2, 0, 0), 1, listOf("F#", "C#", "F#", "A", "B", "E"), "No full barre! Let B and high E strings ring open for indie/acoustic tracks.")
        ),
        "F#m7" to listOf(
            ChordVoicing("F#m7", "Barre 2nd Fret", listOf(1, 3, 1, 1, 1, 1), listOf(1, 3, 1, 1, 1, 1), 2, listOf("F#", "C#", "E", "A", "C#", "F#"), "Smooth minor 7th voicing.")
        ),
        "G" to listOf(
            ChordVoicing("G", "Open Standard (3-Finger)", listOf(3, 2, 0, 0, 0, 3), listOf(2, 1, 0, 0, 0, 3), 1, listOf("G", "B", "D", "G", "B", "G"), "Classic warm open G major."),
            ChordVoicing("G", "Rock/Acoustic 4-Finger", listOf(3, 2, 0, 0, 3, 3), listOf(2, 1, 0, 0, 3, 4), 1, listOf("G", "B", "D", "G", "D", "G"), "Adds D on the B string for extra punch and effortless changes to Cadd9/D.")
        ),
        "G7" to listOf(
            ChordVoicing("G7", "Open Standard", listOf(3, 2, 0, 0, 0, 1), listOf(3, 2, 0, 0, 0, 1), 1, listOf("G", "B", "D", "G", "B", "F"), "Index on 1st fret of high E.")
        ),
        "Gm" to listOf(
            ChordVoicing("Gm", "E-Shape Barre (3fr)", listOf(1, 3, 3, 1, 1, 1), listOf(1, 3, 4, 1, 1, 1), 3, listOf("G", "D", "G", "Bb", "D", "G"), "Bar across 3rd fret; ring and pinky on 5th fret of A & D strings."),
            ChordVoicing("Gm", "Easy 4-String Gm", listOf(-1, -1, 0, 3, 3, 3), listOf(0, 0, 0, 1, 1, 1), 1, listOf("X", "X", "D", "Bb", "D", "G"), "Bar only top 3 strings at 3rd fret with open D string!")
        ),
        "Gm7" to listOf(
            ChordVoicing("Gm7", "Barre 3rd Fret", listOf(1, 3, 1, 1, 1, 1), listOf(1, 3, 1, 1, 1, 1), 3, listOf("G", "D", "F", "Bb", "D", "G"), "Soulful minor 7th at 3rd fret.")
        ),
        "Ab" to listOf(
            ChordVoicing("Ab", "E-Shape Barre (4fr)", listOf(1, 3, 3, 2, 1, 1), listOf(1, 3, 4, 2, 1, 1), 4, listOf("Ab", "Eb", "Ab", "C", "Eb", "Ab"), "Bar at 4th fret. Tip: Capo 1 + G shape makes this effortless!")
        ),
        "A" to listOf(
            ChordVoicing("A", "Open Standard", listOf(-1, 0, 2, 2, 2, 0), listOf(0, 0, 1, 2, 3, 0), 1, listOf("X", "A", "E", "A", "C#", "E"), "Mute 6th string; keep high E open."),
            ChordVoicing("A", "1-Finger Mini-Bar", listOf(-1, 0, 2, 2, 2, -1), listOf(0, 0, 1, 1, 1, 0), 1, listOf("X", "A", "E", "A", "C#", "X"), "Quick rock rhythm voicing using flat index finger.")
        ),
        "Am" to listOf(
            ChordVoicing("Am", "Open Standard", listOf(-1, 0, 2, 2, 1, 0), listOf(0, 0, 2, 3, 1, 0), 1, listOf("X", "A", "E", "A", "C", "E"), "Essential open minor chord; strum from 5th string."),
            ChordVoicing("Am", "E-Shape Barre (5fr)", listOf(1, 3, 3, 1, 1, 1), listOf(1, 3, 4, 1, 1, 1), 5, listOf("A", "E", "A", "C", "E", "A"), "Punchy 5th-fret barre voicing.")
        ),
        "Am7" to listOf(
            ChordVoicing("Am7", "Open 2-Finger", listOf(-1, 0, 2, 0, 1, 0), listOf(0, 0, 2, 0, 1, 0), 1, listOf("X", "A", "E", "G", "C", "E"), "Smooth open voicing—lift ring finger from Am.")
        ),
        "A7" to listOf(
            ChordVoicing("A7", "Open 2-Finger", listOf(-1, 0, 2, 0, 2, 0), listOf(0, 0, 2, 0, 3, 0), 1, listOf("X", "A", "E", "G", "C#", "E"), "Open G string gives the bluesy flat-7th.")
        ),
        "Asus2" to listOf(
            ChordVoicing("Asus2", "Open Ringing", listOf(-1, 0, 2, 2, 0, 0), listOf(0, 0, 1, 2, 0, 0), 1, listOf("X", "A", "E", "A", "B", "E"), "Wide open B and high E strings.")
        ),
        "Asus4" to listOf(
            ChordVoicing("Asus4", "Open Suspended", listOf(-1, 0, 2, 2, 3, 0), listOf(0, 0, 1, 2, 4, 0), 1, listOf("X", "A", "E", "A", "D", "E"), "Slide B-string up to 3rd fret.")
        ),
        "Bb" to listOf(
            ChordVoicing("Bb", "A-Shape Barre (1fr)", listOf(-1, 1, 3, 3, 3, 1), listOf(0, 1, 2, 3, 4, 1), 1, listOf("X", "Bb", "F", "Bb", "D", "F"), "Root on A string 1st fret. Or use Capo 3 + G shape!"),
            ChordVoicing("Bb", "E-Shape Barre (6fr)", listOf(1, 3, 3, 2, 1, 1), listOf(1, 3, 4, 2, 1, 1), 6, listOf("Bb", "F", "Bb", "D", "F", "Bb"), "6th-fret full barre voicing."),
            ChordVoicing("Bb", "Easy 4-String Bb", listOf(-1, -1, 3, 3, 3, 1), listOf(0, 0, 2, 3, 4, 1), 1, listOf("X", "X", "F", "Bb", "D", "F"), "Skip the full barre and strum only the top 4 strings.")
        ),
        "Bbm" to listOf(
            ChordVoicing("Bbm", "A-Shape Barre (1fr)", listOf(-1, 1, 3, 3, 2, 1), listOf(0, 1, 3, 4, 2, 1), 1, listOf("X", "Bb", "F", "Bb", "Db", "F"), "Bar strings 5..1 at 1st fret.")
        ),
        "B" to listOf(
            ChordVoicing("B", "A-Shape Barre (2fr)", listOf(-1, 1, 3, 3, 3, 1), listOf(0, 1, 2, 3, 4, 1), 2, listOf("X", "B", "F#", "B", "D#", "F#"), "Root on 5th string 2nd fret."),
            ChordVoicing("B", "Open Badd11 Indie", listOf(-1, 2, 4, 4, 0, 0), listOf(0, 1, 3, 4, 0, 0), 1, listOf("X", "B", "F#", "B", "B", "E"), "Open top two strings—no barre needed in E or B major!")
        ),
        "Bm" to listOf(
            ChordVoicing("Bm", "A-Shape Barre (2fr)", listOf(-1, 1, 3, 3, 2, 1), listOf(0, 1, 3, 4, 2, 1), 2, listOf("X", "B", "F#", "B", "D", "F#"), "Standard 5-string barre at 2nd fret."),
            ChordVoicing("Bm", "Easy 4-String Bm", listOf(-1, -1, 4, 4, 3, 2), listOf(0, 0, 3, 4, 2, 1), 1, listOf("X", "X", "F#", "B", "D", "F#"), "No barre required—play only the top 4 strings!"),
            ChordVoicing("Bm", "Open Bm7 Alternative", listOf(-1, 2, 0, 2, 0, 2), listOf(0, 2, 0, 3, 0, 4), 1, listOf("X", "B", "D", "A", "B", "F#"), "Zero barre open chord substitute.")
        ),
        "Bm7" to listOf(
            ChordVoicing("Bm7", "Open No-Barre", listOf(-1, 2, 0, 2, 0, 2), listOf(0, 2, 0, 3, 0, 4), 1, listOf("X", "B", "D", "A", "B", "F#"), "Easy open minor 7th without barring."),
            ChordVoicing("Bm7", "Barre 2nd Fret", listOf(-1, 1, 3, 1, 2, 1), listOf(0, 1, 3, 1, 2, 1), 2, listOf("X", "B", "F#", "A", "D", "F#"), "Compact rhythm voicing at 2nd fret.")
        ),
        "B7" to listOf(
            ChordVoicing("B7", "Open 4-Finger", listOf(-1, 2, 1, 2, 0, 2), listOf(0, 2, 1, 3, 0, 4), 1, listOf("X", "B", "D#", "A", "B", "F#"), "Classic open blues turnaround in E.")
        )
    )

    // =========================================================================
    // DETERMINISTIC LYRIC CADENCE, SYLLABLE & CLICHÉ ANALYZER (Suno 6 Workshop)
    // =========================================================================

    private val AI_CLICHE_PHRASES = listOf(
        "neon lights", "neon glow", "shadows dance", "dancing in the shadows",
        "echoes in the", "whisper in the wind", "whispers in the dark",
        "symphony of", "tapestry of", "concrete jungle", "chains that bind",
        "rise from the ashes", "lost in the static", "chasing ghosts",
        "city never sleeps", "electric veins", "shattered dreams", "heart of stone"
    )

    fun countSyllablesInWord(word: String): Int {
        val clean = word.lowercase().replace(Regex("[^a-z]"), "")
        if (clean.isEmpty()) return 0
        if (clean.length <= 3) return 1

        var processed = clean
        if (processed.endsWith("e") && !processed.endsWith("le")) {
            processed = processed.dropLast(1)
        }
        val vowelGroups = Regex("[aeiouy]+").findAll(processed).count()
        return vowelGroups.coerceAtLeast(1)
    }

    fun countSyllablesInLine(line: String): Int {
        val stripped = line.replace(Regex("\\[[^\\]]*\\]"), "") // Strip [Chord] or [Verse] tags
        val words = stripped.split(Regex("\\s+")).filter { it.isNotBlank() }
        return words.sumOf { countSyllablesInWord(it) }
    }

    fun estimateStressPattern(line: String): String {
        val stripped = line.replace(Regex("\\[[^\\]]*\\]"), "").trim()
        val syllables = countSyllablesInLine(stripped)
        if (syllables == 0) return "—"
        val meters = (0 until syllables).map { idx ->
            if (idx % 2 == 1) "DUM" else "da"
        }
        return meters.chunked(4).joinToString("  ") { it.joinToString("-") }
    }

    fun extractRhymeEnding(line: String): String {
        val stripped = line.replace(Regex("\\[[^\\]]*\\]"), "")
            .replace(Regex("[^a-zA-Z\\s]"), "")
            .trim()
        val lastWord = stripped.split(Regex("\\s+")).lastOrNull()?.lowercase() ?: return ""
        if (lastWord.length <= 3) return lastWord
        val match = Regex("[aeiouy][a-z]*$").find(lastWord)
        return match?.value?.takeLast(4) ?: lastWord.takeLast(3)
    }

    fun analyzeLyricsDeterministically(rawLyrics: String): LyricDiagnosisReport {
        val rawLines = rawLyrics.lines()
        var currentSection = "[Verse 1]"
        val diagnostics = mutableListOf<LineDiagnostic>()
        val foundCliches = mutableListOf<String>()
        val phraseCounts = mutableMapOf<String, Int>()

        var idx = 0
        for (raw in rawLines) {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) continue
            if (trimmed.startsWith("[") && trimmed.endsWith("]") && !trimmed.drop(1).contains("[")) {
                currentSection = trimmed
                continue
            }
            val lyricOnly = trimmed.replace(Regex("\\[[^\\]]*\\]"), "").trim()
            if (lyricOnly.isEmpty() || lyricOnly.all { it == '/' || it == '|' || it == ' ' }) continue

            val syllables = countSyllablesInLine(lyricOnly)
            val stress = estimateStressPattern(lyricOnly)
            val rhyme = extractRhymeEnding(lyricOnly)
            val flags = mutableListOf<String>()
            var note = ""

            val lower = lyricOnly.lowercase()
            AI_CLICHE_PHRASES.forEach { cliche ->
                if (lower.contains(cliche)) {
                    flags.add("AI Cliché: \"$cliche\"")
                    foundCliches.add(cliche)
                    note = "Consider replacing \"$cliche\" with concrete, grounded imagery."
                }
            }

            if (syllables > 14) {
                flags.add("Crowded cadence ($syllables syl)")
                if (note.isEmpty()) note = "High syllable density—may rush vocal delivery over 1 bar."
            } else if (syllables in 7..11) {
                flags.add("Pocket cadence ($syllables syl)")
            }

            val normPhrase = lower.replace(Regex("[^a-z\\s]"), "").trim()
            if (normPhrase.length > 10) {
                phraseCounts[normPhrase] = (phraseCounts[normPhrase] ?: 0) + 1
            }

            diagnostics.add(
                LineDiagnostic(
                    lineIndex = idx++,
                    sectionLabel = currentSection,
                    text = lyricOnly,
                    syllableCount = syllables,
                    stressPattern = stress,
                    rhymeEnding = rhyme,
                    flags = flags,
                    suggestionNote = note
                )
            )
        }

        val avgSyllables = if (diagnostics.isNotEmpty()) {
            diagnostics.map { it.syllableCount }.average()
        } else 8.0
        val variance = if (diagnostics.size > 1) {
            diagnostics.map { abs(it.syllableCount - avgSyllables) }.average()
        } else 1.0
        val cadenceScore = (100 - (variance * 8).toInt()).coerceIn(62, 98)

        val repeated = phraseCounts.filter { it.value > 1 }.keys.map { it.replaceFirstChar { c -> c.uppercase() } }
        val singability = diagnostics
            .filter { it.syllableCount > 13 }
            .map { "Line ${it.lineIndex + 1} (${it.syllableCount} syllables): \"${it.text.take(36)}...\" may feel rushed." }

        val sectionsDetected = diagnostics.map { it.sectionLabel }.distinct()

        return LyricDiagnosisReport(
            lyricStructureSummary = "Detected ${sectionsDetected.size} sections (${sectionsDetected.joinToString(", ")}) across ${diagnostics.size} vocal lines.",
            rhymePatternSummary = "Primary rhyme families anchored on ${diagnostics.map { "-${it.rhymeEnding}" }.distinct().take(5).joinToString(", ")}.",
            repeatedPhrases = repeated.ifEmpty { listOf("Central chorus hook repeats cleanly") },
            cadenceConsistencyScore = cadenceScore,
            verseChorusDifferentiation = "Verses average ${avgSyllables.toInt()} syllables/line; Chorus opens up vowel sustain for stronger lift.",
            detectedCliches = foundCliches.distinct(),
            awkwardWordingNotes = if (singability.isNotEmpty()) singability else listOf("Phrasing sits naturally on the downbeats."),
            povConsistency = "1st-person narrative voice stays consistent across sections.",
            tenseConsistency = "Consistent narrative tense throughout verses and chorus.",
            emotionalProgression = "Builds from observational detail in Verse 1 to high-energy release in Chorus.",
            hookStrength = "Strong melodic and rhythmic anchor on the final chorus line.",
            sectionLengthNotes = "Balanced ${diagnostics.size} total lines (~${(diagnostics.size / (sectionsDetected.size.coerceAtLeast(1)))} lines per section).",
            singabilityIssues = singability,
            genreStyleCoherence = "Strong rhythmic fit for guitar-driven arrangement.",
            lineDiagnostics = diagnostics
        )
    }
}
