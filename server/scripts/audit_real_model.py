#!/usr/bin/env python3
"""Evidence-producing real-model smoke test, not a claim of musical accuracy.

Synthesises simple clean harmonic triads with known timestamps and analyzes
them using REAL downloaded ONNX weights. Reports segment-level and frame
accuracy. Synthetic material is not a stand-in for multitrack or Suno music.
"""
from __future__ import annotations

import argparse
import json
import math
import tempfile
import time
from collections import Counter
from pathlib import Path

import numpy as np
import soundfile as sf
from fastapi.testclient import TestClient

from app.analysis.beats import estimate_beat_grid
from app.analysis.key import infer_key
from app.analysis.onnx_engine import OnnxChordEngine
from app.analysis.smoothing import smooth_to_beats
from app.main import create_app

SR = 22050
DURATION_PER_CHORD = 3.0
SEQUENCE = [
    ("C", (261.63, 329.63, 392.00)),
    ("Am", (220.00, 261.63, 329.63)),
    ("F", (174.61, 220.00, 261.63)),
    ("G", (196.00, 246.94, 293.66)),
] * 2


def synthesize() -> np.ndarray:
    pieces: list[np.ndarray] = []
    for _, frequencies in SEQUENCE:
        n = round(DURATION_PER_CHORD * SR)
        t = np.arange(n, dtype=np.float64) / SR
        waveform = np.zeros(n, dtype=np.float64)
        for freq in frequencies:
            for partial in (1, 2, 3):
                waveform += np.sin(2 * np.pi * freq * partial * t) / (len(frequencies) * partial ** 1.2)
        # Soft attacks, release; moderate real-audio-like dynamics.
        envelope = np.minimum(t / 0.04, 1.0) * np.minimum((DURATION_PER_CHORD - t) / 0.15, 1.0)
        pieces.append(np.asarray(waveform * np.maximum(0.0, envelope) * 0.25, dtype=np.float32))
    return np.concatenate(pieces)


def chord_class(label: str) -> tuple[str, str] | None:
    """Map enharmonic triads onto root and major/minor quality."""
    pitches = {"C":0,"C#":1,"Db":1,"D":2,"D#":3,"Eb":3,"E":4,"F":5,
               "F#":6,"Gb":6,"G":7,"G#":8,"Ab":8,"A":9,"A#":10,"Bb":10,"B":11}
    root = next((note for note in sorted(pitches, key=len, reverse=True) if label.startswith(note)), None)
    if root is None:
        return None
    suffix = label[len(root):]
    if suffix == "":
        return str(pitches[root]), "major"
    if suffix == "m":
        return str(pitches[root]), "minor"
    return None


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", type=Path, required=True)
    parser.add_argument("--config", type=Path, required=True)
    args = parser.parse_args()

    engine = OnnxChordEngine(model_path=args.model, config_path=args.config)
    audio = synthesize()
    evidence = engine.analyze(audio, SR)
    grid = estimate_beat_grid(audio, SR)
    segments = smooth_to_beats(evidence, grid)
    key = infer_key(segments)
    assert evidence.frames, "Real ONNX produced no frame predictions"
    assert len(engine.labels) == 170, f"Expected 170 classes, got {len(engine.labels)}"
    assert all(np.isfinite(f.confidence) for f in evidence.frames)
    assert all(f.end_sec > f.start_sec for f in evidence.frames)
    assert 0 <= evidence.frames[0].start_sec < 0.1
    assert abs(evidence.frames[-1].end_sec - audio.size / SR) < 0.2

    samples_per_label: list[dict[str, object]] = []
    total = 0
    correct = 0
    for index, (expected, _) in enumerate(SEQUENCE):
        start = index * DURATION_PER_CHORD + 0.45
        end = (index + 1) * DURATION_PER_CHORD - 0.35
        frames = [frame for frame in evidence.frames if start <= frame.start_sec < end]
        guesses = Counter(frame.chord for frame in frames)
        ok = sum(chord_class(frame.chord) == chord_class(expected) for frame in frames)
        total += len(frames)
        correct += ok
        samples_per_label.append({
            "startSec": round(start, 2),
            "expected": expected,
            "dominantPredicted": guesses.most_common(1)[0][0] if guesses else None,
            "correctFrames": ok,
            "totalFrames": len(frames),
        })
    metrics = {
        "testType": "real-weights-synthetic-harmonic-triads",
        "disclaimer": "Synthetic sustained triads only; not representative Suno-song accuracy.",
        "modelVersion": evidence.engine_version,
        "frames": len(evidence.frames),
        "classes": len(engine.labels),
        "overallFrameChordAgreement": round(correct / total, 4) if total else None,
        "tempoBpm": grid.bpm,
        "inferredKey": key.name,
        "resultSegmentCount": len(segments),
        "segmentResults": samples_per_label,
    }
    print("REAL_MODEL_AUDIT_JSON=" + json.dumps(metrics, separators=(",", ":")))

    # Full HTTP -> bounded upload -> FFmpeg decode -> real ONNX inference ->
    # persisted job -> project correction test, with no private audio involved.
    with tempfile.TemporaryDirectory(prefix="dont-fret-real-audit-") as workdir:
        root = Path(workdir)
        wav = root / "synthetic.wav"
        sf.write(wav, audio, SR, subtype="PCM_16")
        app = create_app(analysis_engine=engine, job_root=root / "jobs")
        with TestClient(app) as client:
            accepted = client.post(
                "/api/analyze",
                files={"file": ("synthetic.wav", wav.read_bytes(), "audio/wav")},
                data={"mode": "normal"},
            )
            assert accepted.status_code == 202, accepted.text
            job_id = accepted.json()["jobId"]
            deadline = time.monotonic() + 120
            job = None
            while time.monotonic() < deadline:
                status = client.get("/api/jobs/" + job_id)
                assert status.status_code == 200, status.text
                job = status.json()
                if job["status"] in {"complete", "failed"}:
                    break
                time.sleep(0.1)
            assert job and job["status"] == "complete", job
            result = job["result"]
            assert result["schemaVersion"] == "dont-fret.analysis.v1"
            assert result["analysis"]["sampleRate"] == SR
            assert result["analysis"]["engineVersion"] == evidence.engine_version
            assert len(result["chords"]) > 0

            created = client.post("/api/projects", json={"jobId": job_id})
            assert created.status_code == 201, created.text
            project_id = created.json()["projectId"]
            raw_first = result["chords"][0]["rawChord"]
            edited = client.patch(
                f"/api/projects/{project_id}/corrections",
                json={"corrections": {"0": "Cmaj7"}},
            )
            assert edited.status_code == 200, edited.text
            reopened = client.get(f"/api/projects/{project_id}").json()
            assert reopened["corrections"] == {"0": "Cmaj7"}
            assert reopened["analysisResult"]["chords"][0]["rawChord"] == raw_first

            print(
                "REAL_MODEL_FULL_STACK_JSON="
                + json.dumps(
                    {
                        "httpStatus": accepted.status_code,
                        "jobStatus": job["status"],
                        "source": "synthesized-harmonic-triads",
                        "durationSec": result["track"]["durationSec"],
                        "key": result["analysis"]["key"],
                        "bpm": result["analysis"]["bpm"],
                        "chordCount": len(result["chords"]),
                        "chordSequence": [
                            {"startSec": round(chord["startSec"], 2),
                             "chord": chord["chord"],
                             "lowConfidence": chord["isLowConfidence"]}
                            for chord in result["chords"]
                        ],
                        "projectCorrectionPreservesRaw": True,
                    },
                    separators=(",", ":"),
                ),
                flush=True,
            )

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
