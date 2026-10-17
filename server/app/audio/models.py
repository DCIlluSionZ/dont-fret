from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True, slots=True)
class DecodedAudio:
    path: Path
    sample_rate: int
    channels: int
    duration_sec: float
