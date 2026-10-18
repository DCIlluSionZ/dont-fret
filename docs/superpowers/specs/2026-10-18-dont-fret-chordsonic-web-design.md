# Don't Fret Web Chord Engine Design

## Goal

Turn Don't Fret into a web-first, installable PWA that can take a song upload and produce a musically useful chord sheet with timing, key, BPM, confidence, capo-friendly shapes, playback following, and editable corrections. The experience should compete with the useful parts of ChordSonic without copying its layout, branding, private APIs, or backend.

## Why the current engine must be replaced

The Android implementation currently attempts to derive PCM samples directly from raw uploaded bytes for non-WAV formats, so MP3/AAC/M4A compressed bytes can be interpreted as audio samples. It also caps local DSP analysis to roughly the first 45 seconds and primarily derives note/root summaries rather than a full chord timeline. When the local fallback is used, it constructs a stock diatonic progression from the detected key instead of transcribing the actual progression.

Those behaviours make the current detector unsuitable as the authoritative analysis engine.

## Product direction

Don't Fret becomes web-first and responsive, with PWA installation for phones/tablets/desktops. The existing Android code remains in the repository during the rebuild and is not deleted until the web app has proven feature parity for the parts worth keeping.

The primary result surface is not a waveform editor. It is a musician's chord sheet.

### Sheet view

- title, detected concert key, BPM, time signature and confidence summary
- original sounding chords
- Easy Guitar mode with capo position and playable chord names that preserve concert pitch
- compact bars grouped into Intro / Verse / Chorus / Bridge / Solo / Outro when structure confidence is sufficient
- low-confidence chord markers that can be reviewed
- tap a chord to audition the time region and select a correction
- user corrections are stored separately from raw model output
- print-friendly A4 layout, aiming for one page when readable

### Follow view

- the same chord sheet becomes a vertical play-along view
- active bar/chord follows audio playback
- seek, loop section and playback speed controls
- no mandatory waveform-heavy UI

## Analysis architecture

### Canonical decode

Every supported upload is decoded with FFmpeg to real mono floating-point PCM at 22,050 Hz before analysis. Compressed bytes are never interpreted as PCM. The full requested song duration is processed subject only to explicit upload/duration limits.

### Normal analysis

1. Decode/resample to mono 22.05 kHz PCM.
2. Estimate tempo, beat and downbeat grid.
3. Run a replaceable contextual chord classifier.
4. Convert frame predictions to beat-synchronous chord evidence.
5. Apply confidence gating, silence gating, temporal smoothing and minimum-chord-duration rules.
6. Infer key from the smoothed harmonic sequence.
7. Emit chord segments with timestamps, beats, bars and confidence.

The classifier must sit behind an `AnalyzerEngine` interface so the model can be swapped without rewriting the API or UI.

The first benchmark candidate is ChordMini/ChordNet ONNX because it offers a much broader chord vocabulary than the current hand-built DSP and can run without sending private song audio to an LLM. Its model/training-data provenance must be reviewed before public commercial distribution. Model weights are not committed to the repository until that review is accepted.

### HQ / Deep Analyse

HQ analysis is optional and may be user-selected or suggested when Normal confidence is poor.

1. Decode as above.
2. Run source separation with Demucs/HTDemucs.
3. Build a harmony-focused analysis mix, preferring bass + accompaniment and excluding drums/vocals by default. Guitar/piano stems may be included when available and beneficial.
4. Run the same chord classifier and beat-synchronisation pipeline on the harmony mix.
5. Compare against Normal output and keep the sequence with stronger calibrated evidence rather than blindly preferring HQ.

This mirrors the useful architectural idea discovered during ChordSonic research without depending on ChordSonic itself.

## AI / Gemini role

Gemini is optional and non-authoritative for harmonic transcription. It may be used after deterministic/audio-model analysis for:

- section naming and higher-level structure hints
- lyric alignment assistance
- songwriter notes
- Suno-specific style/production analysis
- natural-language explanation of uncertain passages

It must not invent chord progressions when the audio engine has insufficient confidence. Unknown remains unknown until the user corrects it or another analysis pass resolves it.

API keys must remain server-side and never be embedded in browser JavaScript or committed source.

## Data model

The authoritative analysis result contains:

- track metadata and duration
- sample rate used for analysis
- BPM and time-signature estimate
- beat/downbeat timestamps
- detected key plus confidence
- chord segments: `startSec`, `endSec`, `chord`, `confidence`, `beatStart`, `beatCount`, `barStart`, `isLowConfidence`
- raw model label and corrected/user label separately
- analysis mode and engine version
- optional structural sections

Exports: JSON, CSV and simple text chord sheet in the first release. PDF can follow once print CSS is stable.

## Capo engine

For capo frets 0 through 8, transpose the full sounding progression downward by the capo interval to generate playable shapes. Score each candidate across the whole song using open-chord availability, barre difficulty, chord-shape transitions and accidental readability. Present the best candidates, not merely the lowest fret.

Concert pitch remains unchanged. Changing capo changes displayed shapes, never the sounding harmonic analysis.

## Web stack

- `web/`: Next.js + TypeScript + React, responsive PWA
- `server/`: Python FastAPI analysis service
- FFmpeg for canonical decode
- ONNX Runtime for replaceable chord-model inference
- Demucs as optional HQ worker dependency
- typed shared JSON contract checked in under `contracts/`
- tests for theory/transposition, decode, smoothing, API schema and UI rendering

The existing Android project stays untouched during the first web milestones.

## Deployment shape

Development runs the Next.js app and FastAPI server separately. Production can initially run them on one host behind a single reverse proxy. Heavy HQ jobs must be isolated from request handlers so source separation cannot freeze the web server.

## Non-goals for the first rebuild

- copying ChordSonic's visual design
- using ChordSonic private endpoints in production
- perfect lyric transcription
- automatic DAW project generation
- deleting the Android app before web validation
- pretending low-confidence predictions are certain

## Acceptance criteria for the first useful release

A user can upload MP3/M4A/WAV/FLAC, receive a timed chord progression from actual decoded audio, play the song while the sheet follows, toggle original vs capo-friendly guitar shapes, correct a chord, export the result, and optionally request HQ separation analysis. The system never substitutes a stock progression simply because confidence is low.
