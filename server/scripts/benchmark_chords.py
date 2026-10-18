#!/usr/bin/env python3
from __future__ import annotations

import argparse
from collections import Counter
from pathlib import Path

import soundfile as sf

from app.analysis.onnx_engine import OnnxChordEngine


def main() -> int:
    parser = argparse.ArgumentParser(description="Run Don't Fret's ONNX chord engine on a canonical 22.05 kHz mono WAV.")
    parser.add_argument("audio", type=Path)
    parser.add_argument("--model", type=Path, required=True)
    parser.add_argument("--config", type=Path, required=True)
    args = parser.parse_args()

    samples, sample_rate = sf.read(args.audio, dtype="float32", always_2d=False)
    if getattr(samples, "ndim", 1) != 1:
        parser.error("benchmark audio must already be mono")
    engine = OnnxChordEngine(model_path=args.model, config_path=args.config)
    evidence = engine.analyze(samples, int(sample_rate))

    counts = Counter(frame.chord for frame in evidence.frames)
    print(f"engine={evidence.engine_version}")
    print(f"frames={len(evidence.frames)}")
    print("top_chords=" + ", ".join(f"{chord}:{count}" for chord, count in counts.most_common(12)))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
