from __future__ import annotations

import os
import shlex
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True, slots=True)
class Settings:
    chord_model_path: Path | None
    chord_model_config_path: Path | None
    demucs_command: tuple[str, ...] | None
    demucs_model: str

    @classmethod
    def from_env(cls) -> "Settings":
        model = os.getenv("DONT_FRET_CHORD_MODEL_PATH", "").strip()
        config = os.getenv("DONT_FRET_CHORD_MODEL_CONFIG_PATH", "").strip()
        demucs_raw = os.getenv("DONT_FRET_DEMUCS_COMMAND", "").strip()
        demucs_model = os.getenv("DONT_FRET_DEMUCS_MODEL", "htdemucs").strip() or "htdemucs"
        return cls(
            chord_model_path=Path(model).expanduser() if model else None,
            chord_model_config_path=Path(config).expanduser() if config else None,
            demucs_command=tuple(shlex.split(demucs_raw)) if demucs_raw else None,
            demucs_model=demucs_model,
        )
