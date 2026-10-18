# Don't Fret Web Chord Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a web-first Don't Fret PWA that performs real decoded-audio chord analysis, produces a timed editable chord sheet, and offers optional HQ stem-separated analysis.

**Architecture:** Add a new `web/` Next.js client and `server/` FastAPI analysis service while leaving the Android app intact. All uploads are canonically decoded to 22.05 kHz mono PCM, analysed through a replaceable chord-engine interface, aligned to beats, smoothed, and returned through a versioned contract. HQ mode adds Demucs separation before the same harmonic pipeline.

**Tech Stack:** Next.js, TypeScript, React, Python 3.12, FastAPI, FFmpeg, NumPy/SciPy, ONNX Runtime, optional Demucs, Vitest/Playwright, pytest

**Spec:** `docs/superpowers/specs/2026-10-18-dont-fret-chordsonic-web-design.md`

## Global Constraints

- Keep the existing Android application untouched during the first web milestones.
- Never interpret compressed MP3/AAC/M4A/FLAC bytes directly as PCM.
- Canonical analysis audio is mono PCM at 22,050 Hz.
- Chord classification must be replaceable behind one engine interface.
- Gemini is optional enrichment, never authoritative harmonic transcription.
- Never invent a stock progression when confidence is insufficient.
- API keys remain server-side and are never committed.
- ChordSonic private APIs and UI are not copied or used in production.
- Raw model output and user-corrected chord labels remain distinct.

## Review Focus

- Corrupt/unsupported audio must fail clearly instead of producing fake chords.
- Variable-bitrate MP3/M4A must decode to correct duration and sample rate.
- Silence and spoken-word regions must not become confident chord labels.
- Capo conversion must preserve concert pitch for every displayed shape.
- HQ worker failure must fall back to Normal results without losing the uploaded project.

---

### Task 1: Web and server foundations

**Files:**
- Create: `web/package.json`, `web/tsconfig.json`, `web/next.config.ts`, `web/app/layout.tsx`, `web/app/page.tsx`
- Create: `server/pyproject.toml`, `server/app/main.py`, `server/tests/test_health.py`
- Create: `contracts/analysis-result.schema.json`
- Create: `README-web.md`

**Interfaces:**
- Produces: `GET /api/health -> {"status":"ok"}`
- Produces: versioned JSON schema `dont-fret.analysis.v1`

- [ ] **Step 1:** Write `server/tests/test_health.py` asserting `/api/health` returns HTTP 200 and `{"status":"ok"}`.
- [ ] **Step 2:** Run `cd server && pytest tests/test_health.py -v`; expect failure because the app does not exist.
- [ ] **Step 3:** Implement the minimal FastAPI app and Next.js upload-page shell.
- [ ] **Step 4:** Run server test and `cd web && npm run build`; both must pass.
- [ ] **Step 5:** Commit `feat: scaffold dont fret web app and analysis server`.

### Task 2: Correct audio decode pipeline

**Files:**
- Create: `server/app/audio/decode.py`
- Create: `server/app/audio/models.py`
- Create: `server/tests/audio/test_decode.py`

**Interfaces:**
- Produces: `decode_audio(input_path: Path, output_path: Path) -> DecodedAudio`
- `DecodedAudio`: `path`, `sample_rate=22050`, `channels=1`, `duration_sec`

- [ ] **Step 1:** Add fixtures/tests for WAV plus generated compressed MP3/M4A when FFmpeg is available; assert 22,050 Hz mono output and duration tolerance <= 0.05 s.
- [ ] **Step 2:** Add corrupt-input test asserting typed `AudioDecodeError`, never fabricated samples.
- [ ] **Step 3:** Run decode tests; expect failure.
- [ ] **Step 4:** Implement FFmpeg decode using explicit mono/22.05 kHz output and `ffprobe` duration validation.
- [ ] **Step 5:** Run decode tests; all pass.
- [ ] **Step 6:** Commit `feat: add canonical ffmpeg audio decoding`.

### Task 3: Analysis contract and replaceable chord engine

**Files:**
- Create: `server/app/analysis/types.py`
- Create: `server/app/analysis/engine.py`
- Create: `server/app/analysis/mock_engine.py`
- Create: `server/tests/analysis/test_engine_contract.py`
- Modify: `contracts/analysis-result.schema.json`

**Interfaces:**
- Consumes: canonical PCM from Task 2.
- Produces: `AnalyzerEngine.analyze(samples: np.ndarray, sample_rate: int) -> FrameChordEvidence`
- Produces chord segment fields: `startSec`, `endSec`, `chord`, `confidence`, `beatStart`, `beatCount`, `barStart`, `isLowConfidence`, `rawChord`, `correctedChord`.

- [ ] **Step 1:** Write contract tests asserting engine output is deterministic, timestamps monotonic and confidence in `[0,1]`.
- [ ] **Step 2:** Run tests; expect failure.
- [ ] **Step 3:** Implement protocol/types and a test-only mock engine.
- [ ] **Step 4:** Run contract tests; pass.
- [ ] **Step 5:** Commit `feat: define replaceable chord analysis contract`.

### Task 4: Beat grid, smoothing and key inference

**Files:**
- Create: `server/app/analysis/beats.py`
- Create: `server/app/analysis/smoothing.py`
- Create: `server/app/analysis/key.py`
- Create: `server/tests/analysis/test_smoothing.py`
- Create: `server/tests/analysis/test_key.py`

**Interfaces:**
- Produces: `estimate_beat_grid(samples, sample_rate) -> BeatGrid`
- Produces: `smooth_to_beats(frame_evidence, beat_grid, settings) -> list[ChordSegment]`
- Produces: `infer_key(chord_segments) -> KeyEstimate`

- [ ] **Step 1:** Add synthetic C-Am-F-G evidence test containing one-frame glitches; assert stable four-bar sequence survives smoothing.
- [ ] **Step 2:** Add silence test asserting low-energy regions become `N`/unknown rather than confident harmony.
- [ ] **Step 3:** Add key test for C major progression.
- [ ] **Step 4:** Run tests; expect failure.
- [ ] **Step 5:** Implement beat quantisation, confidence gating, minimum duration and key inference.
- [ ] **Step 6:** Run tests; pass.
- [ ] **Step 7:** Commit `feat: add beat synced chord smoothing and key inference`.

### Task 5: Real ONNX chord classifier adapter

**Files:**
- Create: `server/app/analysis/onnx_engine.py`
- Create: `server/app/analysis/features.py`
- Create: `server/app/config.py`
- Create: `server/tests/analysis/test_onnx_engine.py`
- Modify: `.env.example`

**Interfaces:**
- Implements: `AnalyzerEngine` from Task 3.
- Configuration: model path supplied by environment, not committed model weights.

- [ ] **Step 1:** Write tests using a tiny fixture/stub ONNX graph to prove tensor shapes, deterministic label mapping and failure when model path is absent.
- [ ] **Step 2:** Run tests; expect failure.
- [ ] **Step 3:** Implement 22.05 kHz feature extraction and ONNX Runtime adapter with model metadata/version reporting.
- [ ] **Step 4:** Add a benchmark script for user-provided songs that compares predicted timeline to manually corrected references without uploading them anywhere.
- [ ] **Step 5:** Run tests; pass.
- [ ] **Step 6:** Commit `feat: add pluggable onnx chord classifier`.

### Task 6: Analysis jobs API

**Files:**
- Create: `server/app/jobs/store.py`
- Create: `server/app/routes/analyze.py`
- Create: `server/tests/routes/test_analyze.py`
- Modify: `server/app/main.py`

**Interfaces:**
- Produces: `POST /api/analyze` multipart upload -> job id.
- Produces: `GET /api/jobs/{job_id}` -> queued/running/complete/failed plus analysis result.

- [ ] **Step 1:** Write upload tests for supported file, unsupported extension, corrupt file and oversized file.
- [ ] **Step 2:** Write completed-job schema validation test.
- [ ] **Step 3:** Run tests; expect failure.
- [ ] **Step 4:** Implement bounded local job execution with canonical decode, engine, beat/smoothing and result persistence.
- [ ] **Step 5:** Run tests; pass.
- [ ] **Step 6:** Commit `feat: expose asynchronous chord analysis api`.

### Task 7: HQ harmonic stem analysis

**Files:**
- Create: `server/app/hq/separation.py`
- Create: `server/app/hq/harmony_mix.py`
- Create: `server/tests/hq/test_harmony_mix.py`
- Modify: `server/app/routes/analyze.py`

**Interfaces:**
- Produces: `separate_for_harmony(decoded: DecodedAudio) -> StemSet`
- Produces: `build_harmony_mix(stems: StemSet) -> Path`
- Supports standard stems `bass`, `other`, `drums`, `vocals` and optional `guitar`, `piano`.

- [ ] **Step 1:** Test default harmony mix excludes drums/vocals and includes bass/other.
- [ ] **Step 2:** Test six-stem mix adds guitar/piano when present.
- [ ] **Step 3:** Test separation failure preserves Normal result and returns an HQ warning.
- [ ] **Step 4:** Run tests; expect failure.
- [ ] **Step 5:** Implement optional Demucs adapter in a worker boundary and confidence-based Normal-vs-HQ result selection.
- [ ] **Step 6:** Run tests; pass.
- [ ] **Step 7:** Commit `feat: add optional hq stem separated analysis`.

### Task 8: Guitar capo and chord-shape engine

**Files:**
- Create: `web/lib/music/chords.ts`
- Create: `web/lib/music/capo.ts`
- Create: `web/lib/music/capo.test.ts`

**Interfaces:**
- Produces: `transposeChord(chord, semitones, preferFlats) -> string`
- Produces: `rankCapoOptions(soundingChords, maxFret=8) -> CapoOption[]`

- [ ] **Step 1:** Add concert-pitch invariance tests, including Bb/Eb/F/Gm and sharp-key cases.
- [ ] **Step 2:** Add slash-chord and seventh/extended-chord preservation tests.
- [ ] **Step 3:** Run Vitest; expect failure.
- [ ] **Step 4:** Implement parser/transposition and whole-song difficulty scoring.
- [ ] **Step 5:** Run tests; pass.
- [ ] **Step 6:** Commit `feat: add concert pitch safe capo engine`.

### Task 9: Sheet and Follow web experience

**Files:**
- Create: `web/app/analyze/page.tsx`
- Create: `web/components/upload/AudioDropzone.tsx`
- Create: `web/components/sheet/ChordSheet.tsx`
- Create: `web/components/sheet/ChordBar.tsx`
- Create: `web/components/player/FollowPlayer.tsx`
- Create: `web/lib/api.ts`
- Create: `web/e2e/analyze.spec.ts`

**Interfaces:**
- Consumes: Task 6 analysis API and Task 8 capo engine.
- Produces: Sheet view, Follow view, Original/Easy Guitar toggle, chord correction state and confidence markers.

- [ ] **Step 1:** Write Playwright test against a mocked finished analysis: upload -> progress -> sheet -> capo toggle -> correction -> Follow playback position.
- [ ] **Step 2:** Run e2e test; expect failure.
- [ ] **Step 3:** Implement responsive upload and sheet UI, keeping waveform optional rather than primary.
- [ ] **Step 4:** Implement Follow view with current chord/bar highlight and section looping.
- [ ] **Step 5:** Run unit/e2e tests and `npm run build`; pass.
- [ ] **Step 6:** Commit `feat: add sheet first chord analysis experience`.

### Task 10: Corrections, exports and persistence

**Files:**
- Create: `server/app/projects/store.py`
- Create: `server/app/routes/projects.py`
- Create: `web/lib/exports.ts`
- Create: `server/tests/routes/test_projects.py`
- Create: `web/lib/exports.test.ts`

**Interfaces:**
- Produces persistence for raw result plus user corrections.
- Produces JSON, CSV and text sheet export.

- [ ] **Step 1:** Test correction persistence never overwrites raw classifier label.
- [ ] **Step 2:** Test JSON/CSV/text exports preserve timing and corrected labels.
- [ ] **Step 3:** Run tests; expect failure.
- [ ] **Step 4:** Implement project persistence and export helpers.
- [ ] **Step 5:** Run tests; pass.
- [ ] **Step 6:** Commit `feat: persist chord corrections and exports`.

### Task 11: PWA, deployment and regression gate

**Files:**
- Create: `web/app/manifest.ts`
- Create: `server/Dockerfile`
- Create: `web/Dockerfile`
- Create: `docker-compose.yml`
- Create: `.github/workflows/web-ci.yml`
- Modify: `README-web.md`

**Interfaces:**
- Produces installable responsive PWA and reproducible two-service deployment.

- [ ] **Step 1:** Add CI commands for server tests, web tests and production build.
- [ ] **Step 2:** Add an integration fixture that synthesises C-Am-F-G audio and asserts monotonic non-empty timed output without hard-coding a fake fallback progression.
- [ ] **Step 3:** Add Docker health checks and documented model/HQ environment toggles.
- [ ] **Step 4:** Run full test suite and production builds.
- [ ] **Step 5:** Verify Android files are unchanged from `main`.
- [ ] **Step 6:** Commit `build: add pwa deployment and regression gate`.

## Self-review result

The plan covers canonical decode, chord-engine abstraction, beat/smoothing, model inference, HQ separation, API jobs, capo preservation, sheet/follow UI, corrections, exports and deployment. Commercial model provenance remains an explicit release gate rather than being silently assumed. The first implementation does not delete or refactor the current Android application, which keeps rollback trivial while the web version earns its place.
