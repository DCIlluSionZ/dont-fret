from __future__ import annotations

import math
import shutil
import struct
import subprocess
import wave
from pathlib import Path

import numpy as np
import pytest

from app.audio.decode import decode_audio


def test_stereo_downmix_is_arithmetic_mean_not_energy_preserving_rematrix(tmp_path: Path) -> None:
    ffmpeg = shutil.which("ffmpeg")
    if ffmpeg is None:
        pytest.skip("ffmpeg is not available")

    source = tmp_path / "stereo.wav"
    output = tmp_path / "mono.wav"
    sample_rate = 44_100
    amplitude = 0.25
    with wave.open(str(source), "wb") as wav:
        wav.setnchannels(2)
        wav.setsampwidth(2)
        wav.setframerate(sample_rate)
        frames = bytearray()
        for index in range(sample_rate):
            value = int(amplitude * 32767 * math.sin(2 * math.pi * 440 * index / sample_rate))
            frames.extend(struct.pack("<hh", value, value))
        wav.writeframes(bytes(frames))

    decode_audio(source, output)
    raw = subprocess.run(
        [ffmpeg, "-hide_banner", "-loglevel", "error", "-i", str(output), "-f", "f32le", "-acodec", "pcm_f32le", "-"],
        check=True,
        stdout=subprocess.PIPE,
    ).stdout
    samples = np.frombuffer(raw, dtype="<f4")

    assert float(np.max(np.abs(samples))) == pytest.approx(amplitude, abs=0.015)
