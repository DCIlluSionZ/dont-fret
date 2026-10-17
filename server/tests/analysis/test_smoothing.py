from __future__ import annotations

import numpy as np

from app.analysis.beats import BeatGrid, estimate_beat_grid
from app.analysis.smoothing import SmoothingSettings, smooth_to_beats
from app.analysis.types import ChordFrame, FrameChordEvidence


def _frame(beat: int, chord: str, *, confidence: float = 0.92, rms_db: float = -18.0) -> ChordFrame:
    start = beat * 0.5
    return ChordFrame(
        start_sec=start,
        end_sec=start + 0.5,
        chord=chord,
        confidence=confidence,
        rms_db=rms_db,
    )


def _grid() -> BeatGrid:
    return BeatGrid(
        beat_times=tuple(index * 0.5 for index in range(17)),
        bpm=120.0,
        beats_per_bar=4,
    )


def test_smoothing_removes_one_beat_glitch_from_four_bar_progression() -> None:
    labels = [
        "C", "C", "G", "C",
        "Am", "Am", "Am", "Am",
        "F", "F", "F", "F",
        "G", "G", "G", "G",
    ]
    evidence = FrameChordEvidence(
        frames=tuple(_frame(index, chord) for index, chord in enumerate(labels)),
        engine_version="fixture-v1",
    )

    segments = smooth_to_beats(
        evidence,
        _grid(),
        SmoothingSettings(min_beats_per_chord=4),
    )

    assert [segment.chord for segment in segments] == ["C", "Am", "F", "G"]
    assert [segment.beat_start for segment in segments] == [0, 4, 8, 12]
    assert [segment.beat_count for segment in segments] == [4, 4, 4, 4]
    assert all(not segment.is_low_confidence for segment in segments)


def test_quiet_region_becomes_unknown_instead_of_confident_chord() -> None:
    evidence = FrameChordEvidence(
        frames=tuple(_frame(index, "C", rms_db=-55.0) for index in range(4)),
        engine_version="fixture-v1",
    )
    grid = BeatGrid(
        beat_times=tuple(index * 0.5 for index in range(5)),
        bpm=120.0,
        beats_per_bar=4,
    )

    segments = smooth_to_beats(evidence, grid, SmoothingSettings())

    assert len(segments) == 1
    assert segments[0].chord == "N"
    assert segments[0].raw_chord == "C"
    assert segments[0].is_low_confidence is True


def test_estimate_beat_grid_finds_synthetic_120_bpm_pulses() -> None:
    sample_rate = 22_050
    samples = np.zeros(sample_rate * 8, dtype=np.float32)
    pulse = np.hanning(400).astype(np.float32)
    for beat in np.arange(0.25, 8.0, 0.5):
        start = int(beat * sample_rate)
        samples[start : start + pulse.size] += pulse

    grid = estimate_beat_grid(samples, sample_rate)

    assert grid.bpm == pytest.approx(120.0, abs=3.0)
    assert len(grid.beat_times) >= 12
    assert grid.beats_per_bar == 4


import pytest
