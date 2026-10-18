from __future__ import annotations

import numpy as np

from app.analysis.mock_engine import MockAnalyzerEngine
from app.analysis.types import ChordSegment


def test_mock_engine_is_deterministic_and_monotonic() -> None:
    samples = np.zeros(22_050 * 4, dtype=np.float32)
    engine = MockAnalyzerEngine(labels=("C", "Am", "F", "G"))

    first = engine.analyze(samples, 22_050)
    second = engine.analyze(samples, 22_050)

    assert first == second
    assert first.engine_version == "mock-v1"
    assert [frame.chord for frame in first.frames] == ["C", "Am", "F", "G"]
    assert all(frame.start_sec < frame.end_sec for frame in first.frames)
    assert all(
        left.end_sec <= right.start_sec
        for left, right in zip(first.frames, first.frames[1:], strict=False)
    )
    assert all(0.0 <= frame.confidence <= 1.0 for frame in first.frames)


def test_chord_segment_keeps_raw_and_corrected_labels_distinct() -> None:
    segment = ChordSegment(
        start_sec=0.0,
        end_sec=2.0,
        chord="Cmaj7",
        confidence=0.82,
        beat_start=0,
        beat_count=4,
        bar_start=0,
        is_low_confidence=False,
        raw_chord="C",
        corrected_chord="Cmaj7",
    )

    assert segment.raw_chord == "C"
    assert segment.corrected_chord == "Cmaj7"
    assert segment.chord == "Cmaj7"
