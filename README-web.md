# Don't Fret web rebuild

This branch adds a web-first Don't Fret experience beside the existing Android app. The Android source remains untouched while the web analysis pipeline is proven.

## Shape

- `web/` — Next.js / React musician-facing PWA
- `server/` — FastAPI audio-analysis service
- `contracts/` — versioned result contracts shared by both sides

## Development

### API

```bash
cd server
python -m venv .venv
source .venv/bin/activate
pip install -e '.[dev]'
uvicorn app.main:app --reload
```

Health check: `GET http://localhost:8000/api/health`

### Web

```bash
cd web
npm install
npm run dev
```

The first milestones deliberately do not commit chord-model weights. Real uploaded audio will be decoded with FFmpeg to mono 22,050 Hz PCM before any harmonic analysis. Compressed file bytes are never treated as PCM.
