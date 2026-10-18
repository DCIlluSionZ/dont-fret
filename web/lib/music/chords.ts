const SHARP_NOTES = ["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"] as const;
const FLAT_NOTES = ["C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B"] as const;

const NOTE_TO_PITCH = new Map<string, number>([
  ["C", 0], ["B#", 0],
  ["C#", 1], ["Db", 1],
  ["D", 2],
  ["D#", 3], ["Eb", 3],
  ["E", 4], ["Fb", 4],
  ["F", 5], ["E#", 5],
  ["F#", 6], ["Gb", 6],
  ["G", 7],
  ["G#", 8], ["Ab", 8],
  ["A", 9],
  ["A#", 10], ["Bb", 10],
  ["B", 11], ["Cb", 11],
]);

const CHORD_TOKEN = /^([A-G](?:#|b)?)(.*)$/;

export interface ChordPitchClasses {
  root: number | null;
  bass: number | null;
}

function normalizePitch(value: number): number {
  return ((value % 12) + 12) % 12;
}

function parseToken(token: string): { root: string; suffix: string; pitch: number } | null {
  const match = CHORD_TOKEN.exec(token.trim());
  if (!match) return null;
  const pitch = NOTE_TO_PITCH.get(match[1]);
  if (pitch === undefined) return null;
  return { root: match[1], suffix: match[2], pitch };
}

function transposeToken(token: string, semitones: number, preferFlats: boolean): string {
  const parsed = parseToken(token);
  if (!parsed) return token;
  const target = normalizePitch(parsed.pitch + semitones);
  const root = preferFlats ? FLAT_NOTES[target] : SHARP_NOTES[target];
  return `${root}${parsed.suffix}`;
}

/**
 * Transpose a chord symbol while preserving everything after each note root.
 * Slash-bass notes are transposed independently, so extensions such as maj7,
 * m7b5, add9 and sus4 are never rewritten or simplified.
 */
export function transposeChord(
  chord: string,
  semitones: number,
  preferFlats = false,
): string {
  const trimmed = chord.trim();
  if (!trimmed || trimmed === "N") return trimmed;

  const slashIndex = trimmed.indexOf("/");
  if (slashIndex < 0) {
    return transposeToken(trimmed, semitones, preferFlats);
  }

  const main = trimmed.slice(0, slashIndex);
  const bass = trimmed.slice(slashIndex + 1);
  return `${transposeToken(main, semitones, preferFlats)}/${transposeToken(bass, semitones, preferFlats)}`;
}

/** Return concert-pitch classes for a chord root and optional slash bass. */
export function chordPitchClasses(chord: string): ChordPitchClasses {
  const trimmed = chord.trim();
  if (!trimmed || trimmed === "N") return { root: null, bass: null };

  const slashIndex = trimmed.indexOf("/");
  const main = slashIndex >= 0 ? trimmed.slice(0, slashIndex) : trimmed;
  const bass = slashIndex >= 0 ? trimmed.slice(slashIndex + 1) : null;
  const parsedMain = parseToken(main);
  const parsedBass = bass ? parseToken(bass) : null;

  return {
    root: parsedMain?.pitch ?? null,
    bass: parsedBass?.pitch ?? null,
  };
}

export function chordRootToken(chord: string): string | null {
  const main = chord.trim().split("/", 1)[0];
  return parseToken(main)?.root ?? null;
}
