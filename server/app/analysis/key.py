from __future__ import annotations

import re
from dataclasses import dataclass

from app.analysis.types import ChordSegment


@dataclass(frozen=True, slots=True)
class KeyEstimate:
    name: str | None
    confidence: float


_NOTE_TO_PC = {
    "C": 0,
    "B#": 0,
    "C#": 1,
    "DB": 1,
    "D": 2,
    "D#": 3,
    "EB": 3,
    "E": 4,
    "FB": 4,
    "E#": 5,
    "F": 5,
    "F#": 6,
    "GB": 6,
    "G": 7,
    "G#": 8,
    "AB": 8,
    "A": 9,
    "A#": 10,
    "BB": 10,
    "B": 11,
    "CB": 11,
}
_PC_TO_NAME = ("C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")
_MAJOR_INTERVALS = (0, 2, 4, 5, 7, 9, 11)
_MAJOR_QUALITIES = ("major", "minor", "minor", "major", "major", "minor", "diminished")
_MINOR_INTERVALS = (0, 2, 3, 5, 7, 8, 10)
_MINOR_QUALITIES = ("minor", "diminished", "major", "minor", "major", "major", "major")
_DEGREE_WEIGHTS = (2.2, 1.0, 1.0, 1.4, 1.8, 1.0, 0.7)


def _parse(chord: str) -> tuple[int, str] | None:
    if chord in {"N", "?", ""}:
        return None
    head = chord.split("/", 1)[0].strip()
    match = re.match(r"^([A-Ga-g])([#b]?)(.*)$", head)
    if match is None:
        return None
    note = (match.group(1).upper() + match.group(2)).upper()
    root = _NOTE_TO_PC.get(note)
    if root is None:
        return None
    suffix = match.group(3).lower()
    if "dim" in suffix or "°" in suffix:
        quality = "diminished"
    elif suffix.startswith("m") and not suffix.startswith("maj"):
        quality = "minor"
    else:
        quality = "major"
    return root, quality


def infer_key(chord_segments: list[ChordSegment]) -> KeyEstimate:
    evidence: list[tuple[int, str, float]] = []
    for segment in chord_segments:
        parsed = _parse(segment.chord)
        if parsed is None or segment.is_low_confidence:
            continue
        root, quality = parsed
        weight = max(1, segment.beat_count) * max(0.05, segment.confidence)
        evidence.append((root, quality, weight))

    if not evidence:
        return KeyEstimate(name=None, confidence=0.0)

    scores: list[tuple[float, int, str]] = []
    for tonic in range(12):
        for mode, intervals, qualities in (
            ("major", _MAJOR_INTERVALS, _MAJOR_QUALITIES),
            ("minor", _MINOR_INTERVALS, _MINOR_QUALITIES),
        ):
            score = 0.0
            for chord_root, chord_quality, evidence_weight in evidence:
                for degree, (interval, expected_quality) in enumerate(zip(intervals, qualities, strict=True)):
                    if chord_root == (tonic + interval) % 12 and chord_quality == expected_quality:
                        score += evidence_weight * _DEGREE_WEIGHTS[degree]
                        break
            scores.append((score, tonic, mode))

    scores.sort(reverse=True)
    best_score, tonic, mode = scores[0]
    if best_score <= 0:
        return KeyEstimate(name=None, confidence=0.0)
    runner_up = scores[1][0]
    margin = max(0.0, best_score - runner_up)
    confidence = min(1.0, max(0.05, margin / best_score))
    return KeyEstimate(
        name=f"{_PC_TO_NAME[tonic]} {mode}",
        confidence=confidence,
    )
