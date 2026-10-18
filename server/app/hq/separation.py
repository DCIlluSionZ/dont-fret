from __future__ import annotations

import shutil
import subprocess
from dataclasses import dataclass
from pathlib import Path
from typing import Protocol

import librosa
import numpy as np
import soundfile as sf

from app.audio.decode import CANONICAL_SAMPLE_RATE, decode_audio


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
class HpssHarmonicSeparator:
    """Built-in harmonic-percussive + bass/midrange separator using librosa HPSS.

    Provides zero-dependency HQ harmonic isolation (suppressing broadband drum transients
    and isolating bass fundamentals < 260 Hz from upper harmonic structure) when an
    external Demucs worker is not installed.
    """

    margin_harmonic: float = 1.2
    margin_percussive: float = 4.0
    bass_cutoff_hz: float = 260.0
    model_name: str = "librosa-hpss-bass-harmonic"

    def separate(self, source_path: Path, output_dir: Path) -> StemSet:
        source_path = Path(source_path)
        output_dir = Path(output_dir)
        if not source_path.is_file():
            raise StemSeparationError(f"source audio not found: {source_path.name}")

        output_dir.mkdir(parents=True, exist_ok=True)
        decoded_path = output_dir / "canonical_input.wav"
        try:
            decode_audio(source_path, decoded_path)
            samples, sample_rate = sf.read(decoded_path, dtype="float32", always_2d=False)
            samples = np.asarray(samples, dtype=np.float32)
            if samples.ndim != 1 or samples.size == 0:
                raise StemSeparationError("decoded audio for HPSS separation is empty")

            stft = librosa.stft(samples, n_fft=2048, hop_length=512)
            harmonic_stft, percussive_stft = librosa.decompose.hpss(
                stft,
                margin=(self.margin_harmonic, self.margin_percussive),
            )
            freqs = librosa.fft_frequencies(sr=int(sample_rate), n_fft=2048)
            bass_mask = (freqs <= self.bass_cutoff_hz)[:, np.newaxis]
            other_mask = (freqs > 90.0)[:, np.newaxis]

            bass_audio = librosa.istft(
                harmonic_stft * bass_mask,
                hop_length=512,
                length=samples.size,
            ).astype(np.float32)
            other_audio = librosa.istft(
                harmonic_stft * other_mask,
                hop_length=512,
                length=samples.size,
            ).astype(np.float32)
            drums_audio = librosa.istft(
                percussive_stft,
                hop_length=512,
                length=samples.size,
            ).astype(np.float32)

            bass_path = output_dir / "bass.wav"
            other_path = output_dir / "other.wav"
            drums_path = output_dir / "drums.wav"
            sf.write(bass_path, bass_audio, CANONICAL_SAMPLE_RATE, subtype="FLOAT")
            sf.write(other_path, other_audio, CANONICAL_SAMPLE_RATE, subtype="FLOAT")
            sf.write(drums_path, drums_audio, CANONICAL_SAMPLE_RATE, subtype="FLOAT")
        except StemSeparationError:
            raise
        except Exception as exc:
            raise StemSeparationError(f"HPSS harmonic separation failed: {exc}") from exc

        return StemSet(
            bass=bass_path,
            other=other_path,
            drums=drums_path,
            model_name=self.model_name,
        )


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
