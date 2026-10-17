from __future__ import annotations

import math
import shutil
import struct
import subprocess
import wave
from pathlib import Path

import pytest

from app.audio.decode import AudioDecodeError, decode_audio


def _write_stereo_wav(path: Path, duration_sec: float = 1.0, sample_rate: int = 44_100) -> None:
    frame_count = int(duration_sec * sample_rate)
    with wave.open(str(path), "wb") as wav:
        wav.setnchannels(2)
        wav.setsampwidth(2)
        wav.setframerate(sample_rate)
        frames = bytearray()
        for i in range(frame_count):
            sample = int(0.35 * 32767 * math.sin(2 * math.pi * 440 * i / sample_rate))
            frames.extend(struct.pack("<hh", sample, sample))
        wav.writeframes(bytes(frames))


def test_decode_wav_to_canonical_mono_22050(tmp_path: Path) -> None:
    source = tmp_path / "source.wav"
    output = tmp_path / "decoded.wav"
    _write_stereo_wav(source)

    decoded = decode_audio(source, output)

    assert decoded.path == output
    assert decoded.sample_rate == 22_050
    assert decoded.channels == 1
    assert decoded.duration_sec == pytest.approx(1.0, abs=0.05)
    assert output.exists()


@pytest.mark.parametrize("extension", ["mp3", "m4a"])
def test_decode_compressed_audio_preserves_duration(tmp_path: Path, extension: str) -> None:
    ffmpeg = shutil.which("ffmpeg")
    if ffmpeg is None:
        pytest.skip("ffmpeg is not available")

    source_wav = tmp_path / "source.wav"
    compressed = tmp_path / f"source.{extension}"
    output = tmp_path / f"decoded-{extension}.wav"
    _write_stereo_wav(source_wav, duration_sec=1.25)

    command = [ffmpeg, "-hide_banner", "-loglevel", "error", "-y", "-i", str(source_wav)]
    if extension == "m4a":
        command += ["-c:a", "aac"]
    command.append(str(compressed))
    subprocess.run(command, check=True)

    decoded = decode_audio(compressed, output)

    assert decoded.sample_rate == 22_050
    assert decoded.channels == 1
    assert decoded.duration_sec == pytest.approx(1.25, abs=0.05)


def test_corrupt_audio_raises_typed_error_instead_of_fabricating_samples(tmp_path: Path) -> None:
    source = tmp_path / "broken.mp3"
    output = tmp_path / "decoded.wav"
    source.write_bytes(b"this is not audio and must never be interpreted as pcm")

    with pytest.raises(AudioDecodeError):
        decode_audio(source, output)

    assert not output.exists()
