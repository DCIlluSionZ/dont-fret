from __future__ import annotations

import librosa
import numpy as np
from numpy.typing import NDArray

CQT_SAMPLE_RATE = 22_050
CQT_HOP_LENGTH = 2_048
CQT_N_BINS = 144
CQT_BINS_PER_OCTAVE = 24
CQT_EPSILON = 1e-6


def estimate_tuning_offset(
    samples: NDArray[np.float32],
    sample_rate: int,
) -> float:
    """Estimate global fractional-bin tuning offset from A440 (Capo-style pitch alignment).

    Returns an offset in [-0.5, 0.5] CQT bins (where 2 bins = 1 semitone at 24 bins/octave),
    or 0.0 when the signal is too short, silent, or non-harmonic.
    """
    if samples.size < sample_rate // 2:
        return 0.0
    rms = float(np.sqrt(np.mean(np.square(samples, dtype=np.float64))))
    if not np.isfinite(rms) or rms < 1e-4:
        return 0.0
    try:
        tuning = float(
            librosa.estimate_tuning(
                y=samples.astype(np.float32, copy=False),
                sr=sample_rate,
                bins_per_octave=CQT_BINS_PER_OCTAVE,
            )
        )
    except Exception:
        return 0.0
    if not np.isfinite(tuning):
        return 0.0
    return float(np.clip(tuning, -0.5, 0.5))


def extract_log_cqt(
    samples: NDArray[np.float32],
    sample_rate: int,
    *,
    align_tuning: bool = True,
) -> NDArray[np.float32]:
    """Extract the ChordMini-compatible log-CQT feature matrix [frames, 144]."""
    if sample_rate != CQT_SAMPLE_RATE:
        raise ValueError(f"Chord CQT requires {CQT_SAMPLE_RATE} Hz audio")
    if samples.ndim != 1:
        raise ValueError("Chord CQT requires mono audio")
    if samples.size == 0:
        return np.empty((0, CQT_N_BINS), dtype=np.float32)

    tuning = estimate_tuning_offset(samples, sample_rate) if align_tuning else 0.0
    cqt = librosa.cqt(
        y=samples.astype(np.float32, copy=False),
        sr=sample_rate,
        hop_length=CQT_HOP_LENGTH,
        fmin=librosa.note_to_hz("C1"),
        n_bins=CQT_N_BINS,
        bins_per_octave=CQT_BINS_PER_OCTAVE,
        tuning=tuning,
        norm=1,
        sparsity=0.01,
        window="hann",
        scale=True,
        pad_mode="constant",
    )
    return np.log(np.abs(cqt) + CQT_EPSILON).T.astype(np.float32, copy=False)
