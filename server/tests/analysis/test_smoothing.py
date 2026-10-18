from __future__ import annotations

import numpy as np
import pytest

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


def test_unknown_x_label_is_preserved_as_raw_but_never_displayed_as_a_chord() -> None:
    grid = BeatGrid(beat_times=(0.0, 0.5, 1.0), bpm=120.0)
    evidence = FrameChordEvidence(
        frames=(
            ChordFrame(0.0, 0.5, "X", 0.99, -10.0),
            ChordFrame(0.5, 1.0, "X", 0.99, -10.0),
        ),
        engine_version="real-model-vocab",
    )
    segments = smooth_to_beats(evidence, grid)
    assert len(segments) == 1
    assert segments[0].chord == "N"
    assert segments[0].raw_chord == "X"
    assert segments[0].is_low_confidence is True


def test_default_preserves_two_beat_g_between_d_chords() -> None:
    """Two-beat pickup chords are musical changes, not bridge glitches."""
    labels = ["D"] * 4 + ["G"] * 2 + ["D"] * 4 + ["C"] * 2
    evidence = FrameChordEvidence(
        frames=tuple(_frame(i, ch) for i, ch in enumerate(labels)),
        engine_version="fixture-v1",
    )
    grid = BeatGrid(tuple(i * .5 for i in range(len(labels) + 1)), bpm=120)
    segments = smooth_to_beats(evidence, grid)
    assert [s.chord for s in segments] == ["D", "G", "D", "C"]
    assert [s.beat_count for s in segments] == [4, 2, 4, 2]


def test_medium_confidence_keeps_candidate_but_marks_it_for_review() -> None:
    """A 0.42 candidate should be editable, not concealed as N or confidently trusted."""
    grid = BeatGrid((0.0, 0.5, 1.0), bpm=120)
    evidence = FrameChordEvidence(
        frames=tuple(_frame(i, "G", confidence=0.42) for i in range(2)),
        engine_version="fixture-v1",
    )
    segments = smooth_to_beats(evidence, grid)
    assert len(segments) == 1
    assert segments[0].chord == "G"
    assert segments[0].raw_chord == "G"
    assert segments[0].is_low_confidence is True


def test_low_confidence_candidate_remains_unknown() -> None:
    grid = BeatGrid((0.0, 0.5, 1.0), bpm=120)
    evidence = FrameChordEvidence(
        frames=tuple(_frame(i, "G", confidence=0.32) for i in range(2)),
        engine_version="fixture-v1",
    )
    segments = smooth_to_beats(evidence, grid)
    assert len(segments) == 1
    assert segments[0].chord == "N"
    assert segments[0].raw_chord == "G"
    assert segments[0].is_low_confidence is True


def test_one_beat_bridge_glitch_is_still_removed() -> None:
    labels = ["D"] * 4 + ["G"] + ["D"] * 4
    evidence = FrameChordEvidence(
        frames=tuple(_frame(i, ch) for i, ch in enumerate(labels)),
        engine_version="fixture-v1",
    )
    grid = BeatGrid(tuple(i * .5 for i in range(len(labels) + 1)), bpm=120)
    segments = smooth_to_beats(evidence, grid)
    assert [s.chord for s in segments] == ["D"]
    assert segments[0].raw_chord == "D"


def test_one_beat_low_confidence_transition_glitch_between_different_chords_is_absorbed() -> None:
    """A 1-beat low-confidence passing glitch (e.g. D -> Bm(0.41) -> G) is absorbed into the stronger neighbor."""
    frames = (
        *(_frame(i, "D", confidence=0.92) for i in range(0, 4)),
        _frame(4, "Bm", confidence=0.41),
        *(_frame(i, "G", confidence=0.88) for i in range(5, 9)),
    )
    evidence = FrameChordEvidence(frames=frames, engine_version="fixture-v1")
    grid = BeatGrid(tuple(i * 0.5 for i in range(10)), bpm=120)
    segments = smooth_to_beats(evidence, grid)
    assert [s.chord for s in segments] == ["D", "G"]
    assert [s.beat_count for s in segments] == [5, 4]
