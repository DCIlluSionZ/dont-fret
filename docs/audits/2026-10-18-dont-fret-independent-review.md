# Don't Fret web rebuild: independent audit, 18 October 2026

**Scope:** Review of `tahlia/chordsonic-web-rebuild` at `b63c2676184fb071ce64294decf07fed51ca0946`, with fixes isolated on `tahlia/dont-fret-audit-20261018`. Original Android source and `main` were not changed. No merge, live deployment, or paid LLM/API invocation was performed.

**Source evidence:** [Independent CI run 39075923401](https://github.com/DCIlluSionZ/dont-fret/actions/runs/39075923401) (both jobs passed); [model card](https://huggingface.co/musetric/chordmini-onnx); [official Hub repository API](https://huggingface.co/api/models/musetric/chordmini-onnx?blobs=true).

## Verified checks

| Gate | Result | Evidence and limitations |
| --- | --- | --- |
| Android source unchanged | PASS | All original `app/`, Gradle and settings source blobs match `main`; no changes/deletions. |
| FastAPI regression suite | PASS | **33 passed**, one deprecation warning, on Python 3.12. |
| Next.js UI and Playwright browser flow | PASS | Unit tests, mocked-API browser upload/sheet/capo/correction/Follow/save/export, and production build. Browser test uses a mocked API: not a real end-to-end browser/model test. |
| Model source integrity | PASS after fix | The initial installer failed closed: model card claimed **17,080,918 bytes**, SHA `cfe77034...`; current Hub delivered **17,080,550 bytes**, SHA `1faee8e1...`. Official Hub LFS metadata confirms the latter at immutable commit `8ab85fc0f61439900cd07ac21257a43eac57075c`. Installer now pins that commit and exact SHA, retaining fail-closed checks. `config.json` remains SHA `1f26c11e...`. |
| Real ONNX model load and inference | PASS | Actual 170-class `chordnet.onnx` was loaded by ONNX Runtime CPU and processed **258 frames** from synthesized harmonic triads. This was not a stub checkpoint. |
| Real ONNX frame chord test | PASS, narrow scope | Eight sustained C / Am / F / G chord regions (each twice) yielded **100% frame agreement inside evaluated stable windows**, excluding 0.45 seconds after each change and 0.35 seconds before the next. This does **not** demonstrate transitions, vocals, mixed-instrument or real-song performance. |
| Full API pipeline using real weights | PASS | Actual synthesized 24s WAV uploaded with HTTP 202, FFmpeg decoded, ONNX model classified, job reached `complete`, result schema and sample rate were correct. Saved project retained unmodified raw labels after a user correction. Key estimated **C major**. |
| Temporal chord sheet quality | CONDITIONAL | Full pipeline returned **13 chord segments** for eight expected sustained chord regions: all eight chords appeared in the correct order; five extra segments were low-confidence `N` regions near changes/end. Needs genuine transition tolerance and musician review. |
| Tempo/real downbeat accuracy | NOT PROVEN | Beat estimator returned **113.46 BPM** on a synthetic file with chord attacks but no asserted click-track/tempo truth. Beats per bar defaults to four, not measured. No BPM accuracy claim is justified by this fixture. |
| HQ Demucs and stem fidelity | NOT PROVEN | Functional tests of adapters/fallback pass, but real stem-separated music and timing/accuracy are unmeasured; Demucs is optional and absent from the base Docker image. |
| Reproducible Docker smoke / mobile PWA | NOT RUN | Dockerfiles and local Compose configuration exist, but image boot/health, install flow, and offline use were not independently exercised. |
| Real Suno music benchmark | BLOCKED | No authorized, manually scored ground-truth Suno recordings were supplied for this review. A fake/reference-model agreement statistic cannot substitute for this gate. |
| Public/paid release | NO-GO | Missing auth and per-user ownership, raw-audio retention and quotas, upload rate limiting, public privacy controls, real-song accuracy evidence, and independently validated model-training-data provenance. |

## Critical or major findings addressed on this audit branch

1. **P0, verified model mismatch:** The project's old checksum and model-card SHA differed from the immutable Hub artifact. The old integrity gate correctly refused it. The reviewer checked the official revision and LFS SHA independently, pinned the immutable version, and kept checksum/size verification. Weight integrity **does not** establish training-data permissions.
2. **P0, real inference blocked:** The model's real `config.json` uses `chordVocab` (170 classes); code only accepted `labels`/`vocabulary` aliases. Added parsing and full regression coverage of `min6`, `maj6`, `minmaj7`. Preserved the model's special `X` label as raw evidence while displaying `N`/unknown, not a fabricated chord.
3. **P1, unbounded audio processing:** Added a default **900 second input-duration limit**, **15 second ffprobe timeouts**, **180 second FFmpeg timeout**, and explicit mapping of the first audio stream to avoid channel-info mismatch. Added duration-rejection and timeout tests. This is a minimum local safety improvement, not a complete public upload-DoS defense.
4. **P1, no real-model test in original CI:** Added a separate audit workflow with hash-verified model download, actual inference, synthetic ground-truth frame metrics and a real end-to-end API/project workflow, explicitly distinguishing narrow synthetic results from product-quality proof.

## Unresolved release blockers

- **Private-local beta only:** No authentication, owner-scoped projects, upload admission/rate limiting or per-user storage budgets. Multipart bodies can consume temporary disk *before* the application-level 50 MiB guard and bounded queue gate. Source files/decoded intermediates persist without an automatic retention/deletion policy. Never expose this API on the internet as shipped.
- **Chords on real music:** Need at least three authorized, manually annotated Suno/recorded songs, ideally with mixed vocals, electric guitar, bass, drums, a slow passage, silence and complex chords. Measure time-weighted chord symbol accuracy (WCSR), root/quality accuracy, transition timing within ±0.25 s and ±0.5 s, BPM/double-half-time ambiguity, key, unknown coverage and chord-sheet usability. Compare side-by-side to independent ChordSonic results rather than assuming parity.
- **Model license and training data:** Upstream code/weights are labelled MIT, but the checkpoint's training-data lineage is not documented sufficiently for a confident commercial licensing statement. Keep model use research-only until independently cleared.
- **Beat and HQ:** Onset autocorrelation is not an authoritative beat/downbeat tracker; 4/4 is assumed; HQ separation is unproven with real stems, and model softmax confidence alone does not guarantee separation improved accuracy.
- **Deployment and supply chain:** Docker image boot not tested, PWA/mobile install not tested, browser E2E intercepts the backend, and dependencies are not all locked reproducibly. Do not expose the experimental API publicly.

## Recommendation and next step

**GO for an isolated, single-user local accuracy study; NO-GO for public or commercial release.** The model finally works end to end, but that is only a prerequisite. On Ben's Mac, install verified research artifacts from the audit branch, run the local server/web pair, analyze representative audio, and compare generated sheets against annotated references. Keep raw model output distinct from manual corrections. Prioritize meaningful chord timing and confidence calibration *after* observing failures on real songs.

This report is a review of current repository evidence. It does not claim to resume or inspect uncommitted scratch files from the quota-blocked Work session.
