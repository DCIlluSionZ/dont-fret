import type { AnalysisResult } from "../../lib/api";
import { ChordBar } from "./ChordBar";

interface Props {
  result: AnalysisResult;
  displayChords: string[];
  activeIndex: number;
  capoFret: number | null;
  easy: boolean;
  onEdit: (index: number) => void;
}

export function ChordSheet({
  result,
  displayChords,
  activeIndex,
  capoFret,
  easy,
  onEdit,
}: Props) {
  return (
    <section className="sheet-paper" aria-label="Chord sheet">
      <div className="sheet-topline"><span>DON&apos;T FRET / CHORD SHEET</span><span>CONCERT-PITCH SAFE</span></div>
      <h2>{result.track.title}</h2>
      <div className="sheet-facts">
        <span>KEY <b>{result.analysis.key ?? "Unknown"}</b></span>
        <span>TEMPO <b>{result.analysis.bpm ? Math.round(result.analysis.bpm) + " BPM" : "Unknown"}</b></span>
        <span>METER <b>{result.analysis.beatsPerBar ? result.analysis.beatsPerBar + "/4 (assumed)" : "Not determined"}</b></span>
        <span>GUITAR <b>{easy && capoFret !== null ? "Capo " + capoFret : "Original"}</b></span>
      </div>
      <div className="section-heading"><h3>Full song</h3><span>Tap a chord to correct it</span></div>
      {result.chords.length > 0 ? (
        <div className="chord-grid">
          {result.chords.map((segment, index) => (
            <ChordBar
              key={index}
              index={index}
              segment={segment}
              displayChord={displayChords[index] ?? segment.chord}
              isActive={index === activeIndex}
              onEdit={onEdit}
            />
          ))}
        </div>
      ) : (
        <p className="sheet-empty">No reliable chord changes were detected. Try HQ, or use a clearer recording.</p>
      )}
      <div className="sheet-footer">
        <span>Timing from audio analysis · Low-confidence chords are marked for review</span>
        <span>DON&apos;T FRET</span>
      </div>
    </section>
  );
}
