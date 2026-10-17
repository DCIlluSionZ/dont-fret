package com.example

import com.example.domain.MusicTheoryEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun capoEquivalence_BbMajor_to_Capo3_GShapes_isExact() {
        val originalChords = listOf("Bb", "Eb", "F", "Gm")
        val options = MusicTheoryEngine.calculateGuitarOptions(
            originalSoundingKey = "Bb Major",
            soundingChords = originalChords,
            userTransposeSemitones = 0
        )
        val openOption = options.first { it.id == "easier_open" }
        assertEquals("Bb Major", openOption.soundingKey)
        assertEquals(3, openOption.capoFret)
        assertEquals(listOf("G", "C", "D", "Em"), openOption.playedShapes)
    }

    @Test
    fun transposeChord_and_syllableCounter_areDeterministic() {
        assertEquals("G", MusicTheoryEngine.transposeChord("Bb", -3))
        assertEquals("C", MusicTheoryEngine.transposeChord("Eb", -3))
        assertEquals("D", MusicTheoryEngine.transposeChord("F", -3))
        assertEquals("Em", MusicTheoryEngine.transposeChord("Gm", -3))

        val report = MusicTheoryEngine.analyzeLyricsDeterministically(
            "[Verse 1]\nWalking under neon lights where shadows dance tonight"
        )
        assertTrue(report.detectedCliches.isNotEmpty())
        assertTrue(report.lineDiagnostics.first().syllableCount > 8)
    }
}
