from app.analysis.key import infer_key
from app.analysis.types import ChordSegment


def _segment(index: int, chord: str) -> ChordSegment:
    return ChordSegment(
        start_sec=index * 2.0,
        end_sec=(index + 1) * 2.0,
        chord=chord,
        confidence=0.9,
        beat_start=index * 4,
        beat_count=4,
        bar_start=index,
        is_low_confidence=False,
        raw_chord=chord,
    )


def test_infer_key_prefers_c_major_for_c_am_f_g_progression() -> None:
    estimate = infer_key([
        _segment(0, "C"),
        _segment(1, "Am"),
        _segment(2, "F"),
        _segment(3, "G"),
    ])

    assert estimate.name == "C major"
    assert 0.0 < estimate.confidence <= 1.0


def test_infer_key_uses_dominant_seventh_to_disambiguate_tonic() -> None:
    """Between C and G major, a G7 dominant chord resolves ambiguity toward C major."""
    estimate = infer_key([
        _segment(0, "C"),
        _segment(1, "G7"),
    ])

    assert estimate.name == "C major"
    assert estimate.confidence > 0.1
