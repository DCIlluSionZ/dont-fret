import { transposeChord } from "./chords";

const OPEN_SHAPES = new Set([
  "C", "C7", "Cmaj7", "Cadd9",
  "D", "Dm", "D7", "Dm7", "Dsus2", "Dsus4",
  "E", "Em", "E7", "Em7", "Esus4",
  "G", "G7", "Gmaj7",
  "A", "Am", "A7", "Am7", "Asus2", "Asus4",
  "Fmaj7",
]);

const COMMON_BARRE_SHAPES = new Set([
  "F", "Fm", "F7", "Fm7",
  "F#", "F#m", "F#7", "F#m7",
  "Gb", "Gbm", "Gb7", "Gbm7",
  "G#", "G#m", "G#7", "G#m7",
  "Ab", "Abm", "Ab7", "Abm7",
  "Bb", "Bbm", "Bb7", "Bbm7",
  "A#", "A#m", "A#7", "A#m7",
  "B", "Bm", "B7", "Bm7",
  "C#", "C#m", "C#7", "C#m7",
  "Db", "Dbm", "Db7", "Dbm7",
  "D#", "D#m", "D#7", "D#m7",
  "Eb", "Ebm", "Eb7", "Ebm7",
  "Gm", "Cm",
]);

export interface CapoOption {
  capoFret: number;
  playedChords: string[];
  mapping: Record<string, string>;
  openChordCount: number;
  barreChordCount: number;
  score: number;
}

function mainShape(chord: string): string {
  return chord.split("/", 1)[0].trim();
}

function scoreShape(chord: string): { score: number; open: boolean; barre: boolean } {
  const shape = mainShape(chord);
  if (OPEN_SHAPES.has(shape)) return { score: 16, open: true, barre: false };
  if (COMMON_BARRE_SHAPES.has(shape)) return { score: -14, open: false, barre: true };
  return { score: 2, open: false, barre: false };
}

/**
 * Rank capo positions for a whole song.
 *
 * A capo raises a played shape by N semitones, therefore every candidate shape
 * is the sounding chord transposed by -N. This identity is never relaxed by
 * the difficulty score: shape + capo always equals the original concert pitch.
 */
export function rankCapoOptions(
  soundingChords: string[],
  maxFret = 8,
): CapoOption[] {
  if (!Number.isInteger(maxFret) || maxFret < 0) {
    throw new RangeError("maxFret must be a non-negative integer");
  }

  const cleaned = soundingChords.map((chord) => chord.trim()).filter(Boolean);
  if (cleaned.length === 0) return [];

  const options: CapoOption[] = [];
  for (let capoFret = 0; capoFret <= maxFret; capoFret += 1) {
    const playedChords = cleaned.map((chord) => transposeChord(chord, -capoFret, false));
    const mapping: Record<string, string> = {};
    let openChordCount = 0;
    let barreChordCount = 0;
    let score = 0;

    cleaned.forEach((soundingChord, index) => {
      const played = playedChords[index];
      mapping[soundingChord] = played;
      const shapeScore = scoreShape(played);
      score += shapeScore.score;
      if (shapeScore.open) openChordCount += 1;
      if (shapeScore.barre) barreChordCount += 1;
    });

    // Reward an entire progression that can stay in familiar open-position shapes.
    if (openChordCount === cleaned.length) score += 20;

    // High capos are useful, but a lower equivalent position is nicer unless the
    // extra fret buys a materially easier whole-song shape set.
    score -= capoFret * 1.5;

    options.push({
      capoFret,
      playedChords,
      mapping,
      openChordCount,
      barreChordCount,
      score,
    });
  }

  return options.sort((left, right) => {
    if (right.score !== left.score) return right.score - left.score;
    if (right.openChordCount !== left.openChordCount) return right.openChordCount - left.openChordCount;
    if (left.barreChordCount !== right.barreChordCount) return left.barreChordCount - right.barreChordCount;
    return left.capoFret - right.capoFret;
  });
}
