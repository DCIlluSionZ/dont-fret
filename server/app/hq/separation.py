from __future__ import annotations

import shutil
import subprocess
from dataclasses import dataclass
from pathlib import Path
from typing import Protocol


class StemSeparationError(RuntimeError):
    """Raised when an HQ separator cannot produce usable harmonic stems."""


@dataclass(frozen=True, slots=True)
class StemSet:
    bass: Path
    other: Path
    drums: Path | None = None
    vocals: Path | None = None
    guitar: Path | None = None
    piano: Path | None = None
    model_name: str = "unknown"

    def harmonic_paths(self) -> tuple[Path, ...]:
        paths: list[Path] = [self.bass, self.other]
        if self.guitar is not None:
            paths.append(self.guitar)
        if self.piano is not None:
            paths.append(self.piano)
        return tuple(paths)


class StemSeparator(Protocol):
    def separate(self, source_path: Path, output_dir: Path) -> StemSet:
        ...


@dataclass(frozen=True, slots=True)
class DemucsCliSeparator:
    """Optional Demucs-compatible CLI adapter.

    The chord pipeline depends on StemSeparator, not Demucs itself. This keeps HQ
    source separation replaceable and avoids making a heavyweight ML dependency a
    requirement for Normal analysis.
    """

    command: tuple[str, ...] = ("demucs",)
    model_name: str = "htdemucs"
    timeout_sec: int = 900

    def separate(self, source_path: Path, output_dir: Path) -> StemSet:
        source_path = Path(source_path)
        output_dir = Path(output_dir)
        if not source_path.is_file():
            raise StemSeparationError(f"source audio not found: {source_path.name}")
        if not self.command:
            raise StemSeparationError("Demucs command is empty")
        executable = self.command[0]
        if Path(executable).is_absolute():
            available = Path(executable).is_file()
        else:
            available = shutil.which(executable) is not None
        if not available:
            raise StemSeparationError(f"HQ separator command not found: {executable}")

        output_dir.mkdir(parents=True, exist_ok=True)
        process = subprocess.run(
            [*self.command, "-n", self.model_name, "--out", str(output_dir), str(source_path)],
            capture_output=True,
            text=True,
            check=False,
            timeout=self.timeout_sec,
        )
        if process.returncode != 0:
            detail = process.stderr.strip().splitlines()
            message = detail[-1] if detail else "Demucs separation failed"
            raise StemSeparationError(message)

        stem_dir = output_dir / self.model_name / source_path.stem
        required = {name: stem_dir / f"{name}.wav" for name in ("bass", "other")}
        missing = [name for name, path in required.items() if not path.is_file()]
        if missing:
            raise StemSeparationError(
                "HQ separator did not produce required stems: " + ", ".join(missing)
            )

        def optional(name: str) -> Path | None:
            path = stem_dir / f"{name}.wav"
            return path if path.is_file() else None

        return StemSet(
            bass=required["bass"],
            other=required["other"],
            drums=optional("drums"),
            vocals=optional("vocals"),
            guitar=optional("guitar"),
            piano=optional("piano"),
            model_name=self.model_name,
        )
