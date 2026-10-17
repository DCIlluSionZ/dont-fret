from __future__ import annotations

import os
import shutil
import threading
import uuid
from pathlib import Path
from typing import Any

import numpy as np
import soundfile as sf
from fastapi import APIRouter, File, HTTPException, Request, UploadFile, status

from app.analysis.beats import estimate_beat_grid
from app.analysis.engine import AnalyzerEngine
from app.analysis.key import infer_key
from app.analysis.onnx_engine import OnnxChordEngine, OnnxModelError
from app.analysis.smoothing import smooth_to_beats
from app.analysis.types import ChordSegment
from app.audio.decode import CANONICAL_SAMPLE_RATE, AudioDecodeError, decode_audio
from app.config import Settings
from app.jobs.store import JobQueueFull, JobStore

router = APIRouter()
SUPPORTED_EXTENSIONS = {".mp3", ".m4a", ".wav", ".flac"}
SUPPORTED_LABEL = "MP3, M4A, WAV, FLAC"
DEFAULT_MAX_UPLOAD_BYTES = 50 * 1024 * 1024
UPLOAD_CHUNK_BYTES = 1024 * 1024
_engine_init_lock = threading.Lock()


def _engine_for(request: Request) -> AnalyzerEngine:
    existing = request.app.state.analysis_engine
    if existing is not None:
        return existing

    with _engine_init_lock:
        existing = request.app.state.analysis_engine
        if existing is not None:
            return existing
        settings = Settings.from_env()
        if settings.chord_model_path is None or settings.chord_model_config_path is None:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Chord model is not configured on this server.",
            )
        try:
            engine = OnnxChordEngine(
                model_path=settings.chord_model_path,
                config_path=settings.chord_model_config_path,
            )
        except OnnxModelError as exc:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail=f"Chord model is unavailable: {exc}",
            ) from exc
        request.app.state.analysis_engine = engine
        return engine


def _segment_json(segment: ChordSegment) -> dict[str, Any]:
    return {
        "startSec": segment.start_sec,
        "endSec": segment.end_sec,
        "chord": segment.chord,
        "confidence": segment.confidence,
        "beatStart": segment.beat_start,
        "beatCount": segment.beat_count,
        "barStart": segment.bar_start,
        "isLowConfidence": segment.is_low_confidence,
        "rawChord": segment.raw_chord,
        "correctedChord": segment.corrected_chord,
    }


def _analyze_job(
    *,
    store: JobStore,
    job_id: str,
    source_path: Path,
    filename: str,
    engine: AnalyzerEngine,
) -> None:
    store.mark_running(job_id)
    try:
        decoded = decode_audio(source_path, store.job_dir(job_id) / "decoded.wav")
        samples, sample_rate = sf.read(decoded.path, dtype="float32", always_2d=False)
        samples = np.asarray(samples, dtype=np.float32)
        if samples.ndim != 1 or int(sample_rate) != CANONICAL_SAMPLE_RATE:
            raise AudioDecodeError("Decoded audio is not canonical mono 22.05 kHz PCM")

        beat_grid = estimate_beat_grid(samples, int(sample_rate))
        frame_evidence = engine.analyze(samples, int(sample_rate))
        segments = smooth_to_beats(frame_evidence, beat_grid)
        key = infer_key(segments)

        result = {
            "schemaVersion": "dont-fret.analysis.v1",
            "track": {
                "title": Path(filename).stem,
                "durationSec": decoded.duration_sec,
            },
            "analysis": {
                "sampleRate": CANONICAL_SAMPLE_RATE,
                "mode": "normal",
                "engineVersion": frame_evidence.engine_version,
                "bpm": beat_grid.bpm,
                "key": key.name,
                "keyConfidence": key.confidence if key.name is not None else None,
                "beatsPerBar": beat_grid.beats_per_bar,
                "beatTimes": list(beat_grid.beat_times),
            },
            "chords": [_segment_json(segment) for segment in segments],
        }
        store.complete(job_id, result)
    except Exception as exc:
        store.fail(job_id, str(exc) or exc.__class__.__name__)


async def _save_bounded_upload(upload: UploadFile, target: Path, max_bytes: int) -> None:
    written = 0
    try:
        with target.open("wb") as handle:
            while True:
                chunk = await upload.read(UPLOAD_CHUNK_BYTES)
                if not chunk:
                    break
                written += len(chunk)
                if written > max_bytes:
                    raise HTTPException(
                        status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
                        detail=f"Audio upload exceeds the {max_bytes // (1024 * 1024)} MiB limit.",
                    )
                handle.write(chunk)
    except BaseException:
        target.unlink(missing_ok=True)
        raise
    finally:
        await upload.close()


@router.post("/api/analyze", status_code=status.HTTP_202_ACCEPTED)
async def analyze_audio(request: Request, file: UploadFile = File(...)) -> dict[str, str]:
    filename = Path(file.filename or "upload").name
    extension = Path(filename).suffix.lower()
    if extension not in SUPPORTED_EXTENSIONS:
        await file.close()
        raise HTTPException(
            status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE,
            detail=f"Supported audio: {SUPPORTED_LABEL}",
        )

    engine = _engine_for(request)
    store: JobStore = request.app.state.job_store
    max_upload_bytes: int = request.app.state.max_upload_bytes

    incoming = store.root / "_incoming"
    incoming.mkdir(parents=True, exist_ok=True)
    temp_path = incoming / f"{uuid.uuid4()}{extension}"
    await _save_bounded_upload(file, temp_path, max_upload_bytes)

    job_id = store.create(filename)
    source_path = store.job_dir(job_id) / f"source{extension}"
    shutil.move(str(temp_path), source_path)

    try:
        store.submit(
            job_id,
            lambda: _analyze_job(
                store=store,
                job_id=job_id,
                source_path=source_path,
                filename=filename,
                engine=engine,
            ),
        )
    except JobQueueFull as exc:
        store.fail(job_id, str(exc))
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail="Analysis queue is full. Try again shortly.",
        ) from exc

    return {"jobId": job_id, "status": "queued"}


@router.get("/api/jobs/{job_id}")
def get_job(request: Request, job_id: str) -> dict[str, Any]:
    store: JobStore = request.app.state.job_store
    record = store.get(job_id)
    if record is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Analysis job not found")
    return record
