# Don't Fret web rebuild

This private web-first prototype adds a Next.js chord sheet and FastAPI chord analyzer beside the original Android application. The Android source remains untouched.

## Features

Real FFmpeg decoding of WAV/MP3/M4A/FLAC; replaceable ONNX chord classifier; beat-aligned timing, key and confidence; optional stem-separated HQ analysis; original and capo-equivalent chord sheets; Follow playback; separate manual corrections, durable saved projects and JSON/CSV/text/A4 exports.

## Run locally

Requires Python 3.12, Node 22, FFmpeg, and a compatible chord ONNX model.

API terminal:

~~~sh
cd server
python -m venv .venv
source .venv/bin/activate
pip install -e '.[dev]'
uvicorn app.main:app --reload
~~~

Web terminal:

~~~sh
cd web
npm install
npm run dev
~~~

Open http://127.0.0.1:3000/analyze. Web requests under /analysis-api/ proxy to localhost:8000/api/. Set ANALYSIS_API_ORIGIN before the Next.js build when changing that upstream.

## Research-only model

No binary weights or secret keys are committed. Without a configured model the API returns HTTP 503 on analysis upload; it does not fabricate chord progressions.

Reference model: https://huggingface.co/musetric/chordmini-onnx. The model card labels it MIT but does not document upstream training-data provenance. Use it for private research, not as a commercially cleared dependency. The model card's published ONNX SHA-256 was stale relative to the current Hub repository. On 2026-10-18 the official Hub API metadata at revision `8ab85fc0f61439900cd07ac21257a43eac57075c` independently gave LFS SHA-256 `1faee8e1cf168300afe0f47517e76836ac200c3841b0fb430fad7dfea5ad6394`, size `17,080,550` bytes; the download produced exactly those bytes. This review pins the immutable revision and checks that hash. **Weight integrity is not proof of dataset provenance or chord-recognition accuracy.**

To download those immutable research artifacts with independent SHA-256 checks:

~~~sh
python server/scripts/fetch_research_model.py --destination models --i-understand-provenance-unknown
~~~

Then set:

~~~sh
export DONT_FRET_CHORD_MODEL_PATH=/absolute/path/to/models/chordnet.onnx
export DONT_FRET_CHORD_MODEL_CONFIG_PATH=/absolute/path/to/models/config.json
~~~

Run a measured benchmark on your own songs before treating chord accuracy as solved. The decoder already handles the model's required arithmetic-mean stereo downmix.

## Optional local Docker packaging

After downloading the model into ./models, run docker compose up --build and open http://127.0.0.1:3000. This binds the web port to localhost only and persists jobs and projects in a volume. The Compose/Dockerfiles have not yet passed independent image-build tests. HQ/Demucs is not installed in the base image and falls back to Normal if no separator is configured.

Do NOT expose this prototype to the public internet until authentication, per-user isolation, storage quotas, cleanup, HTTPS and privacy controls exist. The source also needs an approved model-provenance decision.

## Verification and outstanding gates

GitHub Actions exercises server unit/integration tests, browser upload/sheet/capo/correction/Follow/save/export flow, and Next.js build. Still open: trained-model real-song benchmark, model-license review, Docker image smoke test, automated audio/job cleanup, offline PWA install QA and public deployment security.
