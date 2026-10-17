from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True, slots=True)
class Settings:
    chord_model_path: Path | None
    chord_model_config_path: Path | None

    @classmethod
    def from_env(cls) -> "Settings":
        model = os.getenv("DONT_FRET_CHORD_MODEL_PATH", "").strip()
        config = os.getenv("DONT_FRET_CHORD_MODEL_CONFIG_PATH", "").strip()
        return cls(
            chord_model_path=Path(model).expanduser() if model else None,
            chord_model_config_path=Path(config).expanduser() if config else None,
        )
