from __future__ import annotations

import shutil
import threading
import uuid
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import numpy as np
import soundfile as sf
from fastapi import APIRouter, File, Form, HTTPException, Request, UploadFile, status

from app.analysis.beats import BeatGrid, estimate_beat_grid
from app.analysis.engine import AnalyzerEngine
from app.analysis.key import infer_key
from app.analysis.onnx_engine import OnnxChordEngine, OnnxModelError
from app.analysis.smoothing import smooth_to_beats
from app.analysis.types import ChordSegment
from app.audio.decode import CANONICAL_SAMPLE_RATE, AudioDecodeError, decode_audio
from app.config import Settings
from app.hq.harmony_mix import build_harmony_mix
from app.hq.separation import StemSeparator
from app.jobs.store import JobQueueFull, JobStore

router = APIRouter()
SUPPORTED_EXTENSIONS = {".mp3", ".m4a", ".wav", ".flac"}
SUPPORTED_LABEL = "MP3, M4A, WAV, FLAC"
DEFAULT_MAX_UPLOAD_BYTES = 50 * 1024 * 1024
UPLOAD_CHUNK_BYTES = 1024 * 1024
_engine_init_lock = threading.Lock()


@dataclass(frozen=True, slots=True)
class _AnalysisPass:
    segments: tuple[ChordSegment, ...]
    engine_version: str
    key_name: str | None
    key_confidence: float | None
    confidence_score: float


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


def _confidence_score(segments: tuple[ChordSegment, ...]) -> float:
    usable = [segment for segment in segments if segment.chord != "N"]
    total_beats = sum(segment.beat_count for segment in usable)
    if total_beats <= 0:
        return 0.0
    return sum(segment.confidence * segment.beat_count for segment in usable) / total_beats


def _run_pass(
    samples: np.ndarray,
    sample_rate: int,
    beat_grid: BeatGrid,
    engine: AnalyzerEngine,
) -> _AnalysisPass:
    frame_evidence = engine.analyze(samples, sample_rate)
    segments = tuple(smooth_to_beats(frame_evidence, beat_grid))
    key = infer_key(list(segments))
    return _AnalysisPass(
        segments=segments,
        engine_version=frame_evidence.engine_version,
        key_name=key.name,
        key_confidence=key.confidence if key.name is not None else None,
        confidence_score=_confidence_score(segments),
    )


def _load_canonical(path: Path) -> tuple[np.ndarray, int]:
    samples, sample_rate = sf.read(path, dtype="float32", always_2d=False)
    samples = np.asarray(samples, dtype=np.float32)
    if samples.ndim != 1 or int(sample_rate) != CANONICAL_SAMPLE_RATE:
        raise AudioDecodeError("Audio is not canonical mono 22.05 kHz PCM")
    return samples, int(sample_rate)


def _analyze_job(
    *,
    store: JobStore,
    job_id: str,
    source_path: Path,
    filename: str,
    requested_mode: str,
    engine: AnalyzerEngine,
    stem_separator: StemSeparator | None,
) -> None:
    store.mark_running(job_id)
    try:
        job_dir = store.job_dir(job_id)
        decoded = decode_audio(source_path, job_dir / "decoded.wav")
        samples, sample_rate = _load_canonical(decoded.path)
        beat_grid = estimate_beat_grid(samples, sample_rate)
        normal_pass = _run_pass(samples, sample_rate, beat_grid, engine)

        selected = normal_pass
        actual_mode = "normal"
        warnings: list[str] = []
        separation_model: str | None = None

        if requested_mode == "hq":
            if stem_separator is None:
                warnings.append("HQ separation is not configured; Normal analysis was preserved.")
            else:
                try:
                    hq_root = job_dir / "hq"
                    stems = stem_separator.separate(source_path, hq_root / "stems")
                    separation_model = stems.model_name
                    harmony_path = build_harmony_mix(stems, hq_root / "harmony.wav")
                    hq_samples, hq_sample_rate = _load_canonical(harmony_path)
                    hq_pass = _run_pass(hq_samples, hq_sample_rate, beat_grid, engine)
                    if hq_pass.segments and hq_pass.confidence_score >= normal_pass.confidence_score:
                        selected = hq_pass
                        actual_mode = "hq"
                    else:
                        warnings.append(
                            "HQ separation did not improve chord confidence; Normal analysis was preserved."
                        )
                except Exception as exc:
                    warnings.append(f"HQ separation unavailable: {str(exc) or exc.__class__.__name__}")

        result = {
            "schemaVersion": "dont-fret.analysis.v1",
            "track": {
                "title": Path(filename).stem,
                "durationSec": decoded.duration_sec,
            },
            "analysis": {
                "sampleRate": CANONICAL_SAMPLE_RATE,
                "requestedMode": requested_mode,
                "mode": actual_mode,
                "engineVersion": selected.engine_version,
                "bpm": beat_grid.bpm,
                "key": selected.key_name,
                "keyConfidence": selected.key_confidence,
                "beatsPerBar": beat_grid.beats_per_bar,
                "beatTimes": list(beat_grid.beat_times),
                "warnings": warnings,
                "separationModel": separation_model,
                "normalConfidenceScore": normal_pass.confidence_score,
                "selectedConfidenceScore": selected.confidence_score,
            },
            "chords": [_segment_json(segment) for segment in selected.segments],
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
                        status_code=status.HTTP_413_CONTENT_TOO_LARGE,
                        detail=f"Audio upload exceeds the {max_bytes // (1024 * 1024)} MiB limit.",
                    )
                handle.write(chunk)
    except BaseException:
        target.unlink(missing_ok=True)
        raise
    finally:
        await upload.close()


@router.post("/api/analyze", status_code=status.HTTP_202_ACCEPTED)
async def analyze_audio(
    request: Request,
    file: UploadFile = File(...),
    mode: str = Form("normal"),
) -> dict[str, str]:
    requested_mode = mode.strip().lower()
    if requested_mode not in {"normal", "hq"}:
        await file.close()
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_CONTENT,
            detail="mode must be 'normal' or 'hq'",
        )

    filename = Path(file.filename or "upload").name
    extension = Path(filename).suffix.lower()
    if extension not in SUPPORTED_EXTENSIONS:
        await file.close()
        raise HTTPException(
            status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE,
            detail=f"Supported audio: {SUPPORTED_LABEL}",
        )

    engine = _engine_for(request)
    stem_separator: StemSeparator | None = request.app.state.stem_separator
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
                requested_mode=requested_mode,
                engine=engine,
                stem_separator=stem_separator,
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
