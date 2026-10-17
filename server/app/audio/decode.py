from __future__ import annotations

import json
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


def decode_audio(input_path: Path, output_path: Path) -> DecodedAudio:
    input_path = Path(input_path)
    output_path = Path(output_path)

    if not input_path.is_file():
        raise AudioDecodeError(f"Audio input does not exist: {input_path.name}")

    ffmpeg = _tool("ffmpeg")
    ffprobe = _tool("ffprobe")
    output_path.parent.mkdir(parents=True, exist_ok=True)
    _cleanup(output_path)

    decode = subprocess.run(
        [
            ffmpeg,
            "-hide_banner",
            "-loglevel",
            "error",
            "-y",
            "-i",
            str(input_path),
            "-vn",
            "-ac",
            str(CANONICAL_CHANNELS),
            "-ar",
            str(CANONICAL_SAMPLE_RATE),
            "-c:a",
            "pcm_f32le",
            str(output_path),
        ],
        capture_output=True,
        text=True,
        check=False,
    )

    if decode.returncode != 0 or not output_path.is_file():
        _cleanup(output_path)
        detail = decode.stderr.strip().splitlines()
        message = detail[-1] if detail else "FFmpeg could not decode this file"
        raise AudioDecodeError(message)

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
    )

    try:
        if probe.returncode != 0:
            raise ValueError(probe.stderr.strip() or "ffprobe failed")
        payload = json.loads(probe.stdout)
        stream = payload["streams"][0]
        duration = float(payload["format"]["duration"])
        sample_rate = int(stream["sample_rate"])
        channels = int(stream["channels"])
        if sample_rate != CANONICAL_SAMPLE_RATE or channels != CANONICAL_CHANNELS or duration <= 0:
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
