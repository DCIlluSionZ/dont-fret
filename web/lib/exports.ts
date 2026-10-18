import type { AnalysisResult, ChordSegment } from "./api";
import { transposeChord } from "./music/chords";

type CorrectionMap = Record<number, string>;

export interface SheetOptions {
  capoFret?: number;
}

function corrected(segment: ChordSegment, index: number, corrections: CorrectionMap): string {
  return corrections[index] ?? segment.correctedChord ?? segment.chord;
}

function csvCell(value: string | number | boolean): string {
  let raw = String(value);
  // Protect spreadsheet viewers from executing a formula supplied in metadata.
  if (/^[=+@\t\r]/.test(raw)) raw = "'" + raw;
  return /[",\r\n]/.test(raw) ? '"' + raw.replace(/"/g, '""') + '"' : raw;
}

export function exportAnalysisJson(
  analysisResult: AnalysisResult,
  corrections: CorrectionMap,
): string {
  return JSON.stringify(
    {
      schemaVersion: "dont-fret.project.v1",
      analysisResult,
      corrections: Object.fromEntries(
        Object.entries(corrections).map(([index, chord]) => [index, chord]),
      ),
    },
    null,
    2,
  );
}

export function exportChordsCsv(
  result: AnalysisResult,
  corrections: CorrectionMap,
): string {
  const lines = [
    "startSec,endSec,playedChord,detectedChord,confidence,beatStart,beatCount,barStart,isLowConfidence",
  ];
  result.chords.forEach((segment, index) => {
    const values = [
      segment.startSec,
      segment.endSec,
      corrected(segment, index, corrections),
      segment.chord,
      segment.confidence,
      segment.beatStart,
      segment.beatCount,
      segment.barStart,
      segment.isLowConfidence,
    ];
    lines.push(values.map(csvCell).join(","));
  });
  return lines.join("\r\n") + "\r\n";
}

export function exportChordSheetText(
  result: AnalysisResult,
  corrections: CorrectionMap,
  options: SheetOptions = {},
): string {
  const lines = [
    "DON'T FRET",
    result.track.title,
    "",
    "Original key: " + (result.analysis.key ?? "Unknown"),
    "Tempo: " + (result.analysis.bpm === null ? "Unknown" : Math.round(result.analysis.bpm) + " BPM"),
  ];
  if (options.capoFret !== undefined) {
    if (!Number.isInteger(options.capoFret) || options.capoFret < 0 || options.capoFret > 12) {
      throw new RangeError("Capo fret must be an integer from 0 to 12");
    }
    lines.push("Capo " + options.capoFret + " · Guitar shapes preserve concert pitch");
  } else {
    lines.push("Original concert-pitch chords");
  }
  lines.push("", "Full song");
  const symbols = result.chords.map((segment, index) => {
    const chord = corrected(segment, index, corrections);
    return options.capoFret === undefined ? chord : transposeChord(chord, -options.capoFret);
  });
  for (let offset = 0; offset < symbols.length; offset += 4) {
    lines.push("| " + symbols.slice(offset, offset + 4).join(" | ") + " |");
  }
  if (symbols.length === 0) lines.push("No reliable chords detected.");
  lines.push("", "Timings and confidence are available in the JSON and CSV exports.");
  return lines.join("\n") + "\n";
}
