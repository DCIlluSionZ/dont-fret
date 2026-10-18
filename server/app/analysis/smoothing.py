from __future__ import annotations

from collections import Counter
from dataclasses import dataclass

from app.analysis.beats import BeatGrid
from app.analysis.types import ChordFrame, ChordSegment, FrameChordEvidence


@dataclass(frozen=True, slots=True)
class SmoothingSettings:
    # Below 0.40, no chord is shown. Between 0.40 and 0.45, keep the
    # detected chord visible but flag it for manual review.
    confidence_threshold: float = 0.40
    review_confidence_threshold: float = 0.45
    quiet_rms_db_threshold: float = -40.0
    # One-beat bridges may be glitches; two-beat changes are valid music.
    min_beats_per_chord: int = 2

    def __post_init__(self) -> None:
        if not 0.0 <= self.confidence_threshold <= 1.0:
            raise ValueError("confidence_threshold must be between 0 and 1")
        if not self.confidence_threshold <= self.review_confidence_threshold <= 1.0:
            raise ValueError("review confidence threshold must be >= display confidence threshold and <= 1")
        if self.min_beats_per_chord <= 0:
            raise ValueError("min_beats_per_chord must be positive")


@dataclass(frozen=True, slots=True)
class _BeatEvidence:
    chord: str
    raw_chord: str
    confidence: float
    low_confidence: bool


def _overlap(frame: ChordFrame, start: float, end: float) -> float:
    return max(0.0, min(frame.end_sec, end) - max(frame.start_sec, start))


def _quantize_beat(
    frames: tuple[ChordFrame, ...],
    start: float,
    end: float,
    settings: SmoothingSettings,
) -> _BeatEvidence:
    overlapping = [(frame, _overlap(frame, start, end)) for frame in frames]
    overlapping = [(frame, overlap) for frame, overlap in overlapping if overlap > 0]
    if not overlapping:
        return _BeatEvidence("N", "N", 0.0, True)

    scores: dict[str, float] = {}
    durations: dict[str, float] = {}
    confidence_mass: dict[str, float] = {}
    for frame, overlap in overlapping:
        weight = overlap * max(frame.confidence, 1e-6)
        scores[frame.chord] = scores.get(frame.chord, 0.0) + weight
        durations[frame.chord] = durations.get(frame.chord, 0.0) + overlap
        confidence_mass[frame.chord] = confidence_mass.get(frame.chord, 0.0) + frame.confidence * overlap

    raw = max(scores, key=scores.get)
    duration = max(durations.get(raw, 0.0), 1e-9)
    confidence = min(1.0, max(0.0, confidence_mass.get(raw, 0.0) / duration))
    selected = [frame for frame, _ in overlapping if frame.chord == raw]
    is_quiet = bool(selected) and all(
        frame.rms_db is not None and frame.rms_db <= settings.quiet_rms_db_threshold
        for frame in selected
    )
    unknown = confidence < settings.confidence_threshold or is_quiet or raw in {"X", "N"}
    needs_review = unknown or confidence < settings.review_confidence_threshold
    return _BeatEvidence(
        chord="N" if unknown else raw,
        raw_chord=raw,
        confidence=confidence,
        low_confidence=needs_review,
    )


def _runs(beats: list[_BeatEvidence]) -> list[tuple[int, int, str]]:
    if not beats:
        return []
    result: list[tuple[int, int, str]] = []
    start = 0
    for index in range(1, len(beats) + 1):
        if index == len(beats) or beats[index].chord != beats[start].chord:
            result.append((start, index, beats[start].chord))
            start = index
    return result


def _remove_bridge_glitches(beats: list[_BeatEvidence], minimum: int) -> list[_BeatEvidence]:
    cleaned = beats[:]
    changed = True
    while changed:
        changed = False
        runs = _runs(cleaned)
        for run_index, (start, end, chord) in enumerate(runs):
            if end - start >= minimum or chord == "N":
                continue
            if 0 < run_index < len(runs) - 1:
                left_start, left_end, left = runs[run_index - 1]
                right_start, right_end, right = runs[run_index + 1]
                if left == right and left != "N":
                    for index in range(start, end):
                        beat = cleaned[index]
                        cleaned[index] = _BeatEvidence(
                            chord=left,
                            raw_chord=beat.raw_chord,
                            confidence=beat.confidence,
                            low_confidence=beat.low_confidence,
                        )
                    changed = True
                    break
                # Capo / HMM transition rule: absorb a 1-beat low-confidence transition
                # glitch (e.g. D -> Bm(1 beat, conf=0.41) -> G) into the stronger adjacent
                # stable chord while preserving raw_chord for auditability.
                if (
                    end - start == 1
                    and cleaned[start].low_confidence
                    and left != "N"
                    and right != "N"
                    and (left_end - left_start >= minimum or right_end - right_start >= minimum)
                ):
                    left_conf = sum(cleaned[i].confidence for i in range(left_start, left_end)) / (
                        left_end - left_start
                    )
                    right_conf = sum(cleaned[i].confidence for i in range(right_start, right_end)) / (
                        right_end - right_start
                    )
                    replacement = left if left_conf >= right_conf else right
                    beat = cleaned[start]
                    cleaned[start] = _BeatEvidence(
                        chord=replacement,
                        raw_chord=beat.raw_chord,
                        confidence=beat.confidence,
                        low_confidence=True,
                    )
                    changed = True
                    break
    return cleaned


def smooth_to_beats(
    frame_evidence: FrameChordEvidence,
    beat_grid: BeatGrid,
    settings: SmoothingSettings | None = None,
) -> list[ChordSegment]:
    settings = settings or SmoothingSettings()
    if len(beat_grid.beat_times) < 2:
        return []

    beat_evidence = [
        _quantize_beat(frame_evidence.frames, start, end, settings)
        for start, end in zip(beat_grid.beat_times, beat_grid.beat_times[1:], strict=False)
    ]
    beat_evidence = _remove_bridge_glitches(beat_evidence, settings.min_beats_per_chord)

    segments: list[ChordSegment] = []
    for start_beat, end_beat, chord in _runs(beat_evidence):
        members = beat_evidence[start_beat:end_beat]
        raw_counts = Counter(member.raw_chord for member in members)
        raw_chord = raw_counts.most_common(1)[0][0]
        confidence = sum(member.confidence for member in members) / len(members)
        low = chord == "N" or any(member.low_confidence for member in members)
        segments.append(
            ChordSegment(
                start_sec=beat_grid.beat_times[start_beat],
                end_sec=beat_grid.beat_times[end_beat],
                chord=chord,
                confidence=confidence,
                beat_start=start_beat,
                beat_count=end_beat - start_beat,
                bar_start=start_beat // beat_grid.beats_per_bar,
                is_low_confidence=low,
                raw_chord=raw_chord,
                corrected_chord=None,
            )
        )
    return segments
