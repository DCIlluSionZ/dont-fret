from __future__ import annotations

import wave
from pathlib import Path

import pytest

from app.audio.decode import AudioDecodeError, decode_audio


def _make_wav(path: Path, seconds: float = 1.5) -> None:
    with wave.open(str(path), "wb") as stream:
        stream.setnchannels(1)
        stream.setsampwidth(2)
        stream.setframerate(22_050)
        stream.writeframes(b"\x00\x00" * round(22_050 * seconds))


def test_decode_rejects_audio_longer_than_allowed_before_writing_pcm(tmp_path: Path) -> None:
    source = tmp_path / "long.wav"
    output = tmp_path / "decoded.wav"
    _make_wav(source, seconds=1.5)

    with pytest.raises(AudioDecodeError, match="duration"):
        decode_audio(source, output, max_duration_sec=1.0)

    assert not output.exists()


def test_decode_converts_ffprobe_timeout_into_typed_error(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> None:
    import subprocess

    source = tmp_path / "valid.wav"
    output = tmp_path / "decoded.wav"
    _make_wav(source)

    def timed_out(*args, **kwargs):
        raise subprocess.TimeoutExpired(cmd="ffprobe", timeout=0.01)

    monkeypatch.setattr("app.audio.decode.subprocess.run", timed_out)
    with pytest.raises(AudioDecodeError, match="timed out"):
        decode_audio(source, output)
    assert not output.exists()
