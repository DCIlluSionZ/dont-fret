from __future__ import annotations

from dataclasses import dataclass

import numpy as np
from numpy.typing import NDArray

from app.analysis.types import ChordFrame, FrameChordEvidence


@dataclass(frozen=True, slots=True)
class MockAnalyzerEngine:
    """Deterministic test engine only. Never used as a production fallback."""

    labels: tuple[str, ...]
    confidence: float = 0.9

    def analyze(
        self,
        samples: NDArray[np.float32],
        sample_rate: int,
    ) -> FrameChordEvidence:
        if sample_rate <= 0:
            raise ValueError("sample_rate must be positive")
        if samples.ndim != 1:
            raise ValueError("samples must be mono")
        if not 0.0 <= self.confidence <= 1.0:
            raise ValueError("confidence must be between 0 and 1")
        if not self.labels or samples.size == 0:
            return FrameChordEvidence(frames=(), engine_version="mock-v1")

        duration_sec = samples.size / float(sample_rate)
        frame_duration = duration_sec / len(self.labels)
        frames = tuple(
            ChordFrame(
                start_sec=index * frame_duration,
                end_sec=(index + 1) * frame_duration,
                chord=label,
                confidence=self.confidence,
            )
            for index, label in enumerate(self.labels)
        )
        return FrameChordEvidence(frames=frames, engine_version="mock-v1")
