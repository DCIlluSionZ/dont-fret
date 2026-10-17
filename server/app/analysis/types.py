from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True, slots=True)
class ChordFrame:
    start_sec: float
    end_sec: float
    chord: str
    confidence: float
    rms_db: float | None = None

    def __post_init__(self) -> None:
        if self.start_sec < 0 or self.end_sec <= self.start_sec:
            raise ValueError("ChordFrame timestamps must be non-negative and increasing")
        if not 0.0 <= self.confidence <= 1.0:
            raise ValueError("ChordFrame confidence must be between 0 and 1")
        if not self.chord:
            raise ValueError("ChordFrame chord must not be empty")


@dataclass(frozen=True, slots=True)
class FrameChordEvidence:
    frames: tuple[ChordFrame, ...]
    engine_version: str

    def __post_init__(self) -> None:
        if not self.engine_version:
            raise ValueError("engine_version must not be empty")
        previous_end = 0.0
        for frame in self.frames:
            if frame.start_sec < previous_end:
                raise ValueError("ChordFrame timestamps must be monotonic and non-overlapping")
            previous_end = frame.end_sec


@dataclass(frozen=True, slots=True)
class ChordSegment:
    start_sec: float
    end_sec: float
    chord: str
    confidence: float
    beat_start: int
    beat_count: int
    bar_start: int
    is_low_confidence: bool
    raw_chord: str
    corrected_chord: str | None = None

    def __post_init__(self) -> None:
        if self.start_sec < 0 or self.end_sec <= self.start_sec:
            raise ValueError("ChordSegment timestamps must be non-negative and increasing")
        if not 0.0 <= self.confidence <= 1.0:
            raise ValueError("ChordSegment confidence must be between 0 and 1")
        if self.beat_start < 0 or self.beat_count <= 0 or self.bar_start < 0:
            raise ValueError("ChordSegment beat and bar values must be non-negative")
        if not self.chord or not self.raw_chord:
            raise ValueError("ChordSegment chord labels must not be empty")
