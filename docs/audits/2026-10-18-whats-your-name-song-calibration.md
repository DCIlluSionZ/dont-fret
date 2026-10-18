# Don't Fret real-song chord calibration, 18 October 2026

**Status:** Research-only local calibration. This is **not** independently annotated ground truth, proof of ChordSonic equivalence, or a guarantee of correct guitar chords.

## Test inputs

- Audio: user-provided *What's Your Name Tonight (Official).m4a*, 231.96 seconds, SHA-256 88b76e6b1c492b12a360ce746c0be13c754064293c0d554a0de83388753b224d.
- Reference: user-provided ChordSonic Deep / Enhanced JSON, 89 timed segments. Its output is a comparison tool, **not** ground truth. Audio and reference export are **not committed** to Git.
- Our classifier: actual checksum-verified 170-class ChordMini ONNX model 1faee8e1cf16 with canonical 22,050 Hz FFmpeg decoding. Results from Mac's live API and offline reprocessing.
- Method: time-weighted exact chord-label overlap, excluding intervals the reference labels N. Not a measured industry-standard weighted chord symbol recall (WCSR). Seventh vs triad differences count as disagreements.

## Before / after

| Metric | Existing defaults (0.45 / 4 beats) | Calibrated defaults (0.40 / 2 beats) |
| --- | ---: | ---: |
| Exact agreement with reference | 86.30% | **89.54%** |
| Our N time where reference labels a chord | 7.59% | **3.92%** |
| Different labelled chord | 6.11% | **6.54%** |
| Number of chord segments | 88 | 83 |
| Number of N segments | 22 | 12 |

Added review_confidence_threshold=0.45: a model candidate with per-beat confidence 0.40–0.45 remains visible but is flagged for manual review. True quiet regions, unknown X, and under-0.40 model predictions continue to show as N.

## Root cause verified from raw model frames

- **37.8–38.8 seconds:** model predicted G on 11 of 13 examined ~0.093-second frames. The old four-beat glitch filter erased the two-beat G in a D–G–D move. The revised sheet keeps G from approximately 38.05–39.07s.
- **156.6–157.8 seconds:** 11 of 12 inspected frames label G. The old filter erased G between Ds. The revised sheet keeps G from approximately 156.61–157.63s.
- **60.0–60.37 seconds:** ChordSonic inserts a transient Bm between D/G; our model transitions D→G instead. This transient is **not validated** as a real Bm chord.
- **226.48–226.76 seconds:** ChordSonic briefly marks Gsus4; the raw model continues G in the sampled region. Treat this embellishment as unverified.

The reference reports rms_db = 0.0 for all segments, including those marked silent, so those RMS fields are not suitable silence-ground-truth. The reference and our key detectors agree on G major; tempo estimates are 117.01 vs 117.91 BPM. Key-confidence scales are not comparable.

## Remaining quality work

1. Manually score chord changes with the songwriter, including short pickups, seventh chords and middle bridge. Do not treat agreement with another algorithm as musical truth.
2. Test additional Suno songs with held vocals, guitar strums and close chord inversions.
3. Benchmark configured HQ harmonic-stem separation. The current Mac service has no HQ separator installed/configured, so HQ requests fall back to Normal with a warning. Demucs and model licensing/compute remain separate gates.
4. Improve beat/downbeat estimation (4/4 currently assumed) and calibrate confidence after human-grounded examples are available.

## Verification and decision

Added unit regression cases for two-beat preservation, one-beat bridge cleanup, medium-confidence review, and low-confidence unknown. 37 Python tests passed locally, including FFmpeg and API tests.

Do not launch commercially or publicly without separate privacy, authentication, retention, HQ and model-license gates. Rescan the song after calibration deploy; previously saved analysis projects do not automatically change.
