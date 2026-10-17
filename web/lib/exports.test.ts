import { describe, expect, it } from "vitest";
import { exportAnalysisJson, exportChordsCsv, exportChordSheetText } from "./exports";
import type { AnalysisResult } from "./api";

const result: AnalysisResult = {
  schemaVersion: "dont-fret.analysis.v1",
  track: { title: "Little, Song", durationSec: 8 },
  analysis: {
    sampleRate: 22050,
    mode: "normal",
    engineVersion: "fixture-v1",
    bpm: 120,
    key: "Bb major",
    keyConfidence: 0.9,
    beatsPerBar: 4,
  },
  chords: [
    { startSec: 0, endSec: 2, chord: "Bb", rawChord: "Bb", correctedChord: null, confidence: .93, beatStart: 0, beatCount: 4, barStart: 0, isLowConfidence: false },
    { startSec: 2, endSec: 4, chord: "Eb", rawChord: "Eb", correctedChord: null, confidence: .9, beatStart: 4, beatCount: 4, barStart: 1, isLowConfidence: false },
    { startSec: 4, endSec: 6, chord: "F", rawChord: "F", correctedChord: null, confidence: .7, beatStart: 8, beatCount: 4, barStart: 2, isLowConfidence: false },
    { startSec: 6, endSec: 8, chord: "Gm", rawChord: "Gm", correctedChord: null, confidence: .9, beatStart: 12, beatCount: 4, barStart: 3, isLowConfidence: false },
  ],
};

describe("project exports", () => {
  it("preserves raw model labels separately from user corrections in JSON", () => {
    const exported = JSON.parse(exportAnalysisJson(result, { 0: "Bb7" }));
    expect(exported.schemaVersion).toBe("dont-fret.project.v1");
    expect(exported.analysisResult.chords[0].chord).toBe("Bb");
    expect(exported.analysisResult.chords[0].rawChord).toBe("Bb");
    expect(exported.corrections["0"]).toBe("Bb7");
    expect(result.chords[0].chord).toBe("Bb");
  });

  it("writes quoted CSV metadata and each timed corrected chord", () => {
    const csv = exportChordsCsv(result, { 0: "Bb7" });
    expect(csv).toContain("startSec,endSec,playedChord,detectedChord");
    expect(csv).toContain("0,2,Bb7,Bb");
    expect(csv).toContain("2,4,Eb,Eb");
  });

  it("preserves original pitch when exporting capo shapes", () => {
    const sheet = exportChordSheetText(result, { 0: "Bb7" }, { capoFret: 3 });
    expect(sheet).toContain("Little, Song");
    expect(sheet).toContain("Original key: Bb major");
    expect(sheet).toContain("Capo 3");
    expect(sheet).toContain("| G7 | C | D | Em |");
  });
});
