package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.domain.MusicTheoryEngine
import com.example.model.GuitarOption
import com.example.model.SongAnalysisProject
import java.io.File
import java.io.FileOutputStream

object SheetExportManager {

    fun copyToClipboard(context: Context, label: String, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    /**
     * Generates a clean, two-line-per-lyric-row Plain Text Guitar Song Sheet
     * where chord names sit directly above the exact word/syllable where changes occur.
     */
    fun buildPlainTextSongSheet(
        project: SongAnalysisProject,
        activeOption: GuitarOption,
        transposeSemitones: Int
    ): String {
        val sb = StringBuilder()
        sb.appendLine("================================================================")
        sb.appendLine("DON'T FRET • DCI STUDIO SONG SHEET")
        sb.appendLine("Designed & Coded with Intelligence")
        sb.appendLine("================================================================")
        sb.appendLine("Title:         ${project.title}")
        sb.appendLine("Sounding Key:  ${activeOption.soundingKey}")
        sb.appendLine(
            "Guitar Mode:   ${activeOption.name} — Play ${activeOption.playedKeyShape} Shapes " +
                if (activeOption.capoFret > 0) "(Capo Fret ${activeOption.capoFret})" else "(No Capo)"
        )
        sb.appendLine("Tempo / Time:  ${project.tempoBpm} BPM (${project.timeSignature}) • ${project.feel}")
        sb.appendLine("Tuning:        ${project.tuning}")
        sb.appendLine("Strumming:     ${project.strummingPattern}")
        sb.appendLine("Played Chords: ${activeOption.playedShapes.joinToString(" – ")}")
        sb.appendLine("----------------------------------------------------------------")
        sb.appendLine()

        for (sec in project.sections) {
            sb.appendLine(sec.label)
            for (rawLine in sec.lines) {
                val (chordRow, lyricRow) = splitBracketLineIntoChordAndLyricRows(
                    rawLine = rawLine,
                    transposeSemitones = transposeSemitones,
                    capoFret = activeOption.capoFret,
                    chordMapping = activeOption.chordMapping
                )
                if (chordRow.isNotBlank()) {
                    sb.appendLine(chordRow)
                }
                if (lyricRow.isNotBlank()) {
                    sb.appendLine(lyricRow)
                }
            }
            sb.appendLine()
        }

        if (project.riffTab.isNotBlank()) {
            sb.appendLine("--- SIGNATURE RIFF / WALKDOWN TAB ---")
            sb.appendLine(project.riffTab)
            sb.appendLine()
        }

        if (project.producerNotes.isNotBlank()) {
            sb.appendLine("--- PRODUCER & GUITAR NOTES ---")
            sb.appendLine(project.producerNotes)
        }
        return sb.toString()
    }

    /**
     * Generates Markdown + SVG Fretboard Lead Sheet (matching the user's HTML demo export).
     */
    fun buildMarkdownSongSheet(
        project: SongAnalysisProject,
        activeOption: GuitarOption,
        transposeSemitones: Int
    ): String {
        val sb = StringBuilder()
        sb.appendLine("# ${project.title}")
        sb.appendLine("> **DON'T FRET by DCI** — *Designed & Coded with Intelligence*")
        sb.appendLine()
        sb.appendLine(
            "**Sounding Key:** ${activeOption.soundingKey} | " +
                "**Played Shapes:** ${activeOption.playedKeyShape} " +
                "(${if (activeOption.capoFret > 0) "Capo Fret ${activeOption.capoFret}" else "No Capo"}) | " +
                "**Tempo:** ${project.tempoBpm} BPM (${project.timeSignature})"
        )
        sb.appendLine("**Strumming:** `${project.strummingPattern}` *(${project.feel})*")
        sb.appendLine("**Played Chords:** `${activeOption.playedShapes.joinToString("  ")}`")
        sb.appendLine()
        sb.appendLine("---")
        sb.appendLine()

        for (sec in project.sections) {
            sb.appendLine("### ${sec.label}")
            sb.appendLine("```text")
            for (rawLine in sec.lines) {
                val (chordRow, lyricRow) = splitBracketLineIntoChordAndLyricRows(
                    rawLine = rawLine,
                    transposeSemitones = transposeSemitones,
                    capoFret = activeOption.capoFret,
                    chordMapping = activeOption.chordMapping
                )
                if (chordRow.isNotBlank()) sb.appendLine(chordRow)
                if (lyricRow.isNotBlank()) sb.appendLine(lyricRow)
            }
            sb.appendLine("```")
            sb.appendLine()
        }

        if (project.riffTab.isNotBlank()) {
            sb.appendLine("### Signature Riff / Walkdown Tab")
            sb.appendLine("```text")
            sb.appendLine(project.riffTab)
            sb.appendLine("```")
        }
        return sb.toString()
    }

    /**
     * Generates a crisp white Studio Printable PDF Song Sheet and launches the Android Share/Print sheet.
     */
    fun exportAndSharePrintablePdf(
        context: Context,
        project: SongAnalysisProject,
        activeOption: GuitarOption,
        transposeSemitones: Int
    ): Result<File> {
        return try {
            val pdf = PdfDocument()
            val pageWidth = 595 // A4 width in PostScript points
            val pageHeight = 842 // A4 height

            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            var page = pdf.startPage(pageInfo)
            var canvas: Canvas = page.canvas

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.rgb(15, 23, 42)
                textSize = 20f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.rgb(37, 99, 235)
                textSize = 9.5f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            }
            val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.rgb(51, 65, 85)
                textSize = 10.5f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
            val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.rgb(153, 27, 27)
                textSize = 11.5f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val chordPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.rgb(185, 28, 28)
                textSize = 10.5f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            }
            val lyricPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.rgb(15, 23, 42)
                textSize = 11f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            }
            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.rgb(203, 213, 225)
                strokeWidth = 1f
            }

            var y = 42f
            val marginX = 40f

            // Header
            canvas.drawText("DON'T FRET • DCI (DESIGNED & CODED WITH INTELLIGENCE)", marginX, y, brandPaint)
            y += 22f
            canvas.drawText(project.title, marginX, y, titlePaint)
            y += 18f

            val capoLabel = if (activeOption.capoFret > 0) "Capo Fret ${activeOption.capoFret}" else "No Capo"
            canvas.drawText(
                "Sounding Key: ${activeOption.soundingKey}   |   Played Shapes: ${activeOption.playedKeyShape} ($capoLabel)   |   Tempo: ${project.tempoBpm} BPM (${project.timeSignature})",
                marginX,
                y,
                metaPaint
            )
            y += 15f
            canvas.drawText(
                "Strumming: ${project.strummingPattern}   |   Chords: ${activeOption.playedShapes.joinToString(" - ")}",
                marginX,
                y,
                metaPaint
            )
            y += 12f
            canvas.drawLine(marginX, y, pageWidth - marginX, y, linePaint)
            y += 20f

            fun checkNewPage(neededHeight: Float) {
                if (y + neededHeight > pageHeight - 45f) {
                    pdf.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    page = pdf.startPage(pageInfo)
                    canvas = page.canvas
                    y = 42f
                }
            }

            for (sec in project.sections) {
                checkNewPage(44f)
                canvas.drawText(sec.label, marginX, y, sectionPaint)
                y += 15f
                for (rawLine in sec.lines) {
                    val (chordRow, lyricRow) = splitBracketLineIntoChordAndLyricRows(
                        rawLine = rawLine,
                        transposeSemitones = transposeSemitones,
                        capoFret = activeOption.capoFret,
                        chordMapping = activeOption.chordMapping
                    )
                    checkNewPage(30f)
                    if (chordRow.isNotBlank()) {
                        canvas.drawText(chordRow, marginX, y, chordPaint)
                        y += 13f
                    }
                    if (lyricRow.isNotBlank()) {
                        canvas.drawText(lyricRow, marginX, y, lyricPaint)
                        y += 16f
                    }
                }
                y += 8f
            }

            // Footer
            canvas.drawLine(marginX, pageHeight - 34f, pageWidth - marginX, pageHeight - 34f, linePaint)
            canvas.drawText(
                "Generated by Don't Fret • DCI — Designed & Coded with Intelligence",
                marginX,
                pageHeight - 20f,
                brandPaint
            )

            pdf.finishPage(page)

            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val safeName = project.title.replace(Regex("[^a-zA-Z0-9]+"), "_").lowercase().ifBlank { "song_sheet" }
            val outFile = File(exportDir, "dont_fret_${safeName}.pdf")
            FileOutputStream(outFile).use { out ->
                pdf.writeTo(out)
            }
            pdf.close()

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outFile
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "${project.title} — Don't Fret Song Sheet")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Export Printable PDF Song Sheet").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)

            Result.success(outFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Converts a single inline bracketed line (e.g. "I heard your [Bb]voice this [Eb]morning")
     * into two aligned rows:
     * Row 1 (Chords): "             G          C"
     * Row 2 (Lyrics): "I heard your voice this morning"
     */
    fun splitBracketLineIntoChordAndLyricRows(
        rawLine: String,
        transposeSemitones: Int,
        capoFret: Int,
        chordMapping: Map<String, String>
    ): Pair<String, String> {
        val chordBuilder = StringBuilder()
        val lyricBuilder = StringBuilder()

        var i = 0
        while (i < rawLine.length) {
            if (rawLine[i] == '[') {
                val closeIdx = rawLine.indexOf(']', i + 1)
                if (closeIdx != -1) {
                    val rawToken = rawLine.substring(i + 1, closeIdx).trim()
                    val soundingTransposed = MusicTheoryEngine.transposeChord(rawToken, transposeSemitones)
                    val playedChord = chordMapping[soundingTransposed]
                        ?: MusicTheoryEngine.transposeChord(soundingTransposed, -capoFret)

                    while (chordBuilder.length < lyricBuilder.length) {
                        chordBuilder.append(' ')
                    }
                    chordBuilder.append(playedChord).append(' ')
                    i = closeIdx + 1
                    continue
                }
            }
            lyricBuilder.append(rawLine[i])
            i++
        }
        return chordBuilder.toString().trimEnd() to lyricBuilder.toString().trimEnd()
    }
}
