import { describe, expect, it } from "vitest";

import { chordPitchClasses, transposeChord } from "./chords";
import { rankCapoOptions } from "./capo";

function expectConcertPitchPreserved(
  sounding: string[],
  played: string[],
  capoFret: number,
) {
  expect(played).toHaveLength(sounding.length);
  sounding.forEach((soundingChord, index) => {
    const concert = chordPitchClasses(soundingChord);
    const shape = chordPitchClasses(played[index]);
    expect(shape.root).not.toBeNull();
    expect(concert.root).not.toBeNull();
    expect(((shape.root as number) + capoFret) % 12).toBe(concert.root);

    if (concert.bass !== null) {
      expect(shape.bass).not.toBeNull();
      expect(((shape.bass as number) + capoFret) % 12).toBe(concert.bass);
    }
  });
}

describe("transposeChord", () => {
  it("preserves slash bass and chord extensions", () => {
    expect(transposeChord("D/F#", 2, false)).toBe("E/G#");
    expect(transposeChord("F#m7b5", -2, false)).toBe("Em7b5");
    expect(transposeChord("Bbmaj7", -3, false)).toBe("Gmaj7");
    expect(transposeChord("Ebsus4/Bb", -3, false)).toBe("Csus4/G");
  });

  it("can spell transposed roots with flats", () => {
    expect(transposeChord("B", 2, true)).toBe("Db");
    expect(transposeChord("F#7", 4, true)).toBe("Bb7");
  });
});

describe("rankCapoOptions", () => {
  it("ranks capo 3 G-C-D-Em for Bb-Eb-F-Gm while preserving concert pitch", () => {
    const sounding = ["Bb", "Eb", "F", "Gm"];
    const [best] = rankCapoOptions(sounding, 8);

    expect(best.capoFret).toBe(3);
    expect(best.playedChords).toEqual(["G", "C", "D", "Em"]);
    expectConcertPitchPreserved(sounding, best.playedChords, best.capoFret);
  });

  it("ranks capo 4 G-C-D-Em for B-E-F#-G#m", () => {
    const sounding = ["B", "E", "F#", "G#m"];
    const [best] = rankCapoOptions(sounding, 8);

    expect(best.capoFret).toBe(4);
    expect(best.playedChords).toEqual(["G", "C", "D", "Em"]);
    expectConcertPitchPreserved(sounding, best.playedChords, best.capoFret);
  });

  it("preserves sounding slash-bass pitch through capo mapping", () => {
    const sounding = ["D/F#", "G/B", "A7/C#"];
    const options = rankCapoOptions(sounding, 8);

    expect(options).not.toHaveLength(0);
    for (const option of options) {
      expectConcertPitchPreserved(sounding, option.playedChords, option.capoFret);
    }
  });

  it("never ranks a capo above the requested maximum", () => {
    expect(rankCapoOptions(["Bb", "Eb", "F", "Gm"], 2).every((option) => option.capoFret <= 2)).toBe(true);
  });
});
