from __future__ import annotations

from pathlib import Path

import numpy as np
import soundfile as sf

from app.audio.decode import CANONICAL_SAMPLE_RATE, decode_audio
from app.hq.separation import StemSeparationError, StemSet


class HarmonyMixError(RuntimeError):
    """Raised when separated harmonic stems cannot be combined safely."""


def build_harmony_mix(stems: StemSet, output_path: Path) -> Path:
    """Build bass + accompaniment mix, explicitly excluding drums and vocals."""
    output_path = Path(output_path)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    canonical_dir = output_path.parent / "canonical-stems"
    canonical_dir.mkdir(parents=True, exist_ok=True)

    harmonic_paths = stems.harmonic_paths()
    if not harmonic_paths:
        raise StemSeparationError("HQ separator produced no harmonic stems")

    decoded_arrays: list[np.ndarray] = []
    for index, source in enumerate(harmonic_paths):
        source = Path(source)
        if not source.is_file():
            raise HarmonyMixError(f"harmonic stem not found: {source.name}")
        canonical = canonical_dir / f"{index}-{source.stem}.wav"
        decode_audio(source, canonical)
        samples, sample_rate = sf.read(canonical, dtype="float32", always_2d=False)
        samples = np.asarray(samples, dtype=np.float32)
        if samples.ndim != 1 or int(sample_rate) != CANONICAL_SAMPLE_RATE:
            raise HarmonyMixError(f"stem did not decode to canonical mono PCM: {source.name}")
        decoded_arrays.append(samples)

    max_length = max(array.size for array in decoded_arrays)
    if max_length == 0:
        raise HarmonyMixError("harmonic stems are empty")
    mix = np.zeros(max_length, dtype=np.float32)
    for array in decoded_arrays:
        mix[: array.size] += array

    peak = float(np.max(np.abs(mix)))
    if not np.isfinite(peak):
        raise HarmonyMixError("harmonic mix contains non-finite samples")
    if peak > 0.98:
        mix *= np.float32(0.98 / peak)

    sf.write(output_path, mix, CANONICAL_SAMPLE_RATE, subtype="FLOAT")
    return output_path
