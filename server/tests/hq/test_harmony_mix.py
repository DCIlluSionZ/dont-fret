from __future__ import annotations

from pathlib import Path

import numpy as np
import pytest
import soundfile as sf

from app.hq.harmony_mix import build_harmony_mix
from app.hq.separation import StemSet

SAMPLE_RATE = 22_050


def _stem(path: Path, value: float, duration_sec: float = 1.0) -> Path:
    samples = np.full(int(SAMPLE_RATE * duration_sec), value, dtype=np.float32)
    sf.write(path, samples, SAMPLE_RATE, subtype="FLOAT")
    return path


def test_standard_harmony_mix_includes_bass_and_other_but_excludes_drums_and_vocals(tmp_path: Path) -> None:
    stems = StemSet(
        bass=_stem(tmp_path / "bass.wav", 0.10),
        other=_stem(tmp_path / "other.wav", 0.20),
        drums=_stem(tmp_path / "drums.wav", 0.40),
        vocals=_stem(tmp_path / "vocals.wav", 0.80),
        model_name="fixture-4stem",
    )

    output = build_harmony_mix(stems, tmp_path / "harmony.wav")
    samples, sample_rate = sf.read(output, dtype="float32", always_2d=False)

    assert sample_rate == SAMPLE_RATE
    assert samples.ndim == 1
    assert float(np.mean(samples)) == pytest.approx(0.30, abs=0.01)


def test_extended_harmony_mix_adds_guitar_and_piano(tmp_path: Path) -> None:
    stems = StemSet(
        bass=_stem(tmp_path / "bass.wav", 0.10),
        other=_stem(tmp_path / "other.wav", 0.20),
        drums=_stem(tmp_path / "drums.wav", 0.40),
        vocals=_stem(tmp_path / "vocals.wav", 0.80),
        guitar=_stem(tmp_path / "guitar.wav", 0.05),
        piano=_stem(tmp_path / "piano.wav", 0.07),
        model_name="fixture-6stem",
    )

    output = build_harmony_mix(stems, tmp_path / "harmony-6.wav")
    samples, _ = sf.read(output, dtype="float32", always_2d=False)

    assert float(np.mean(samples)) == pytest.approx(0.42, abs=0.01)
