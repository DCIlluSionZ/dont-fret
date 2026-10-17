from __future__ import annotations

import librosa
import numpy as np
from numpy.typing import NDArray

CQT_SAMPLE_RATE = 22_050
CQT_HOP_LENGTH = 2_048
CQT_N_BINS = 144
CQT_BINS_PER_OCTAVE = 24
CQT_EPSILON = 1e-6


def extract_log_cqt(
    samples: NDArray[np.float32],
    sample_rate: int,
) -> NDArray[np.float32]:
    """Extract the ChordMini-compatible log-CQT feature matrix [frames, 144]."""
    if sample_rate != CQT_SAMPLE_RATE:
        raise ValueError(f"Chord CQT requires {CQT_SAMPLE_RATE} Hz audio")
    if samples.ndim != 1:
        raise ValueError("Chord CQT requires mono audio")
    if samples.size == 0:
        return np.empty((0, CQT_N_BINS), dtype=np.float32)

    cqt = librosa.cqt(
        y=samples.astype(np.float32, copy=False),
        sr=sample_rate,
        hop_length=CQT_HOP_LENGTH,
        fmin=librosa.note_to_hz("C1"),
        n_bins=CQT_N_BINS,
        bins_per_octave=CQT_BINS_PER_OCTAVE,
        norm=1,
        sparsity=0.01,
        window="hann",
        scale=True,
        pad_mode="constant",
    )
    return np.log(np.abs(cqt) + CQT_EPSILON).T.astype(np.float32, copy=False)
