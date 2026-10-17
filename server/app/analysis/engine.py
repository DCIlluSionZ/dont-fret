from __future__ import annotations

from typing import Protocol

import numpy as np
from numpy.typing import NDArray

from app.analysis.types import FrameChordEvidence


class AnalyzerEngine(Protocol):
    """Replaceable source of frame-level chord evidence.

    Production engines may use ONNX or another classifier, but downstream
    beat/smoothing code depends only on this contract.
    """

    def analyze(
        self,
        samples: NDArray[np.float32],
        sample_rate: int,
    ) -> FrameChordEvidence:
        ...
