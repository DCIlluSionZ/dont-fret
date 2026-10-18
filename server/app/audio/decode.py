from __future__ import annotations

import json
import math
import shutil
import subprocess
from pathlib import Path

from app.audio.models import DecodedAudio

CANONICAL_SAMPLE_RATE = 22_050
CANONICAL_CHANNELS = 1


class AudioDecodeError(RuntimeError):
    """Raised when an upload cannot be decoded into canonical PCM."""


def _tool(name: str) -> str:
    path = shutil.which(name)
    if path is None:
        raise AudioDecodeError(f"Required audio tool '{name}' is not installed")
    return path


def _cleanup(path: Path) -> None:
    try:
        path.unlink(missing_ok=True)
    except OSError:
        pass


def _probe_input_info(ffprobe: str, input_path: Path) -> tuple[int, float]:
    """Bounded metadata probe for the first audio stream and container duration."""
    try:
        probe = subprocess.run(
            [
                ffprobe,
                "-v", "error",
                "-select_streams", "a:0",
                "-show_entries", "stream=channels:format=duration",
                "-of", "json",
                str(input_path),
            ],
            capture_output=True,
            text=True,
            check=False,
            timeout=15,
        )
    except subprocess.TimeoutExpired as exc:
        raise AudioDecodeError("Audio metadata probe timed out") from exc

    try:
        if probe.returncode != 0:
            raise ValueError(probe.stderr.strip() or "ffprobe failed")
        data = json.loads(probe.stdout)
        channels = int(data["streams"][0]["channels"])
        duration = float(data["format"]["duration"])
        if channels < 1 or not math.isfinite(duration) or duration <= 0:
            raise ValueError("invalid audio stream channel count or duration")
        return channels, duration
    except (ValueError, TypeError, KeyError, IndexError, json.JSONDecodeError) as exc:
        raise AudioDecodeError(f"Could not inspect input audio duration and channels: {exc}") from exc


def _arithmetic_mean_pan_filter(channels: int) -> str:
    coefficient = 1.0 / channels
    terms = "+".join(f"{coefficient:.12g}*c{index}" for index in range(channels))
    return f"pan=mono|c0={terms}"


def decode_audio(input_path: Path, output_path: Path, *, max_duration_sec: float = 900.0) -> DecodedAudio:
    input_path = Path(input_path)
    output_path = Path(output_path)

    if not input_path.is_file():
        raise AudioDecodeError(f"Audio input does not exist: {input_path.name}")

    ffmpeg = _tool("ffmpeg")
    ffprobe = _tool("ffprobe")
    if not math.isfinite(max_duration_sec) or max_duration_sec <= 0:
        raise ValueError("max_duration_sec must be positive and finite")
    input_channels, input_duration = _probe_input_info(ffprobe, input_path)
    if input_duration > max_duration_sec:
        raise AudioDecodeError(f"Input audio duration exceeds {max_duration_sec:g} seconds")
    output_path.parent.mkdir(parents=True, exist_ok=True)
    _cleanup(output_path)

    try:
        decode = subprocess.run(
            [
                ffmpeg,
                "-hide_banner", "-loglevel", "error", "-y",
                "-i", str(input_path),
                "-map", "0:a:0",
                "-vn",
                "-af", _arithmetic_mean_pan_filter(input_channels),
                "-ar", str(CANONICAL_SAMPLE_RATE),
                "-c:a", "pcm_f32le",
                str(output_path),
            ],
            capture_output=True,
            text=True,
            check=False,
            timeout=180,
        )
    except subprocess.TimeoutExpired as exc:
        _cleanup(output_path)
        raise AudioDecodeError("Audio decoder timed out") from exc


    if decode.returncode != 0 or not output_path.is_file():
        _cleanup(output_path)
        detail = decode.stderr.strip().splitlines()
        message = detail[-1] if detail else "FFmpeg could not decode this file"
        raise AudioDecodeError(message)

    try:
        probe = subprocess.run(
            [
                ffprobe,
                "-v",
                "error",
                "-select_streams",
                "a:0",
                "-show_entries",
                "stream=sample_rate,channels:format=duration",
                "-of",
                "json",
                str(output_path),
            ],
            capture_output=True,
            text=True,
            check=False,
            timeout=15,
        )
    except subprocess.TimeoutExpired as exc:
        _cleanup(output_path)
        raise AudioDecodeError("Decoded audio validation timed out") from exc

    try:
        if probe.returncode != 0:
            raise ValueError(probe.stderr.strip() or "ffprobe failed")
        payload = json.loads(probe.stdout)
        stream = payload["streams"][0]
        duration = float(payload["format"]["duration"])
        sample_rate = int(stream["sample_rate"])
        channels = int(stream["channels"])
        if sample_rate != CANONICAL_SAMPLE_RATE or channels != CANONICAL_CHANNELS or duration <= 0 or duration > max_duration_sec + 0.1:
            raise ValueError("decoded audio does not match canonical format")
    except (KeyError, IndexError, TypeError, ValueError, json.JSONDecodeError) as exc:
        _cleanup(output_path)
        raise AudioDecodeError(f"Decoded audio validation failed: {exc}") from exc

    return DecodedAudio(
        path=output_path,
        sample_rate=sample_rate,
        channels=channels,
        duration_sec=duration,
    )
