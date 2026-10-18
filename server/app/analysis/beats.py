from __future__ import annotations

from dataclasses import dataclass

import numpy as np
from numpy.typing import NDArray


@dataclass(frozen=True, slots=True)
class BeatGrid:
    beat_times: tuple[float, ...]
    bpm: float | None
    beats_per_bar: int = 4

    def __post_init__(self) -> None:
        if self.beats_per_bar <= 0:
            raise ValueError("beats_per_bar must be positive")
        if self.bpm is not None and self.bpm <= 0:
            raise ValueError("bpm must be positive when known")
        if any(time < 0 for time in self.beat_times):
            raise ValueError("beat times must be non-negative")
        if any(left >= right for left, right in zip(self.beat_times, self.beat_times[1:], strict=False)):
            raise ValueError("beat times must be strictly increasing")


def estimate_beat_grid(
    samples: NDArray[np.float32],
    sample_rate: int,
    *,
    beats_per_bar: int = 4,
    min_bpm: float = 60.0,
    max_bpm: float = 200.0,
) -> BeatGrid:
    """Estimate a simple onset-autocorrelation beat grid from canonical mono PCM.

    This is deliberately dependency-light. It supplies timing to the first web MVP;
    the interface allows a stronger beat/downbeat tracker to replace it later.
    """
    if sample_rate <= 0:
        raise ValueError("sample_rate must be positive")
    if samples.ndim != 1:
        raise ValueError("samples must be mono")
    if min_bpm <= 0 or max_bpm <= min_bpm:
        raise ValueError("invalid BPM search range")

    duration_sec = samples.size / float(sample_rate)
    if samples.size == 0:
        return BeatGrid(beat_times=(), bpm=None, beats_per_bar=beats_per_bar)

    hop = max(1, sample_rate // 100)  # ~10 ms
    frame_count = samples.size // hop
    if frame_count < 8:
        return BeatGrid(
            beat_times=(0.0, duration_sec) if duration_sec > 0 else (),
            bpm=None,
            beats_per_bar=beats_per_bar,
        )

    trimmed = samples[: frame_count * hop].reshape(frame_count, hop)
    envelope = np.sqrt(np.mean(np.square(trimmed, dtype=np.float64), axis=1))
    onset = np.maximum(0.0, np.diff(envelope, prepend=envelope[0]))
    peak = float(np.max(onset))
    if not np.isfinite(peak) or peak < 1e-6:
        return BeatGrid(
            beat_times=(0.0, duration_sec) if duration_sec > 0 else (),
            bpm=None,
            beats_per_bar=beats_per_bar,
        )

    hop_sec = hop / float(sample_rate)
    min_lag = max(1, int(round((60.0 / max_bpm) / hop_sec)))
    max_lag = min(onset.size - 1, int(round((60.0 / min_bpm) / hop_sec)))
    if max_lag <= min_lag:
        return BeatGrid(
            beat_times=(0.0, duration_sec),
            bpm=None,
            beats_per_bar=beats_per_bar,
        )

    correlations = np.array(
        [float(np.dot(onset[lag:], onset[:-lag])) for lag in range(min_lag, max_lag + 1)],
        dtype=np.float64,
    )
    best_offset = int(np.argmax(correlations))
    best_lag = min_lag + best_offset
    if correlations[best_offset] <= 0:
        return BeatGrid(
            beat_times=(0.0, duration_sec),
            bpm=None,
            beats_per_bar=beats_per_bar,
        )

    period_sec = best_lag * hop_sec
    bpm = 60.0 / period_sec
    phase_bins = min(best_lag, onset.size)
    phase = int(np.argmax(onset[:phase_bins])) * hop_sec

    times: list[float] = [0.0]
    current = phase
    if current < 1e-6:
        current = period_sec
    while current < duration_sec - 1e-6:
        if current - times[-1] > 1e-4:
            times.append(float(current))
        current += period_sec
    if duration_sec - times[-1] > 1e-4:
        times.append(float(duration_sec))

    return BeatGrid(
        beat_times=tuple(times),
        bpm=float(bpm),
        beats_per_bar=beats_per_bar,
    )
