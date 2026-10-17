from __future__ import annotations

import hashlib
import json
import math
from collections.abc import Callable
from pathlib import Path
from typing import Any

import numpy as np
import onnxruntime as ort
from numpy.typing import NDArray

from app.analysis.features import CQT_HOP_LENGTH, CQT_N_BINS, CQT_SAMPLE_RATE, extract_log_cqt
from app.analysis.types import ChordFrame, FrameChordEvidence

FeatureExtractor = Callable[[NDArray[np.float32], int], NDArray[np.float32]]


class OnnxModelError(RuntimeError):
    """Raised when the configured chord model cannot be loaded or used safely."""


def _lookup(config: dict[str, Any], names: tuple[str, ...], default: Any = None) -> Any:
    queue: list[dict[str, Any]] = [config]
    lowered = {name.lower() for name in names}
    while queue:
        current = queue.pop(0)
        for key, value in current.items():
            if str(key).lower() in lowered:
                return value
            if isinstance(value, dict):
                queue.append(value)
    return default


_STANDARD_QUALITIES = {
    "maj": "",
    "min": "m",
    "maj7": "maj7",
    "min7": "m7",
    "7": "7",
    "dim": "dim",
    "dim7": "dim7",
    "hdim7": "m7b5",
    "aug": "aug",
    "sus2": "sus2",
    "sus4": "sus4",
    "5": "5",
}


def _guitar_chord_label(model_label: str) -> str:
    """Convert ChordMini root:quality classes to conventional guitar symbols."""
    if ":" not in model_label:
        return model_label
    root, quality = model_label.split(":", 1)
    if root not in {
        "C", "C#", "Db", "D", "D#", "Eb", "E", "F", "F#", "Gb",
        "G", "G#", "Ab", "A", "A#", "Bb", "B",
    }:
        return model_label
    suffix = _STANDARD_QUALITIES.get(quality)
    return root + suffix if suffix is not None else model_label


def _labels_from_config(config: dict[str, Any]) -> tuple[str, ...]:
    raw = _lookup(
        config,
        ("labels", "vocabulary", "chordLabels", "idxToChord", "idx_to_chord"),
    )
    if isinstance(raw, list) and raw and all(isinstance(item, str) for item in raw):
        return tuple(_guitar_chord_label(label) for label in raw)
    if isinstance(raw, dict) and raw:
        try:
            ordered = sorted(((int(key), str(value)) for key, value in raw.items()), key=lambda item: item[0])
        except (TypeError, ValueError):
            ordered = []
        if ordered and [index for index, _ in ordered] == list(range(len(ordered))):
            return tuple(_guitar_chord_label(value) for _, value in ordered)
    raise OnnxModelError("Chord model config does not contain an indexed label vocabulary")


def _softmax(logits: NDArray[np.float32]) -> NDArray[np.float32]:
    shifted = logits - np.max(logits, axis=1, keepdims=True)
    exp = np.exp(shifted, dtype=np.float32)
    denominator = np.sum(exp, axis=1, keepdims=True)
    return exp / np.maximum(denominator, np.finfo(np.float32).tiny)


def _smooth_logits(logits: NDArray[np.float32], window: int) -> NDArray[np.float32]:
    window = max(1, int(window))
    if window % 2 == 0:
        window += 1
    if window == 1 or logits.shape[0] < 2:
        return logits
    radius = window // 2
    padded = np.pad(logits, ((radius, radius), (0, 0)), mode="edge")
    cumulative = np.cumsum(padded, axis=0, dtype=np.float64)
    cumulative = np.vstack((np.zeros((1, logits.shape[1]), dtype=np.float64), cumulative))
    smoothed = (cumulative[window:] - cumulative[:-window]) / float(window)
    return smoothed.astype(np.float32)


def _rms_db_for_frame(samples: NDArray[np.float32], start: int, end: int) -> float:
    chunk = samples[start:end]
    if chunk.size == 0:
        return -120.0
    rms = float(np.sqrt(np.mean(np.square(chunk, dtype=np.float64))))
    if not math.isfinite(rms) or rms <= 1e-8:
        return -120.0
    return max(-120.0, 20.0 * math.log10(rms))


class OnnxChordEngine:
    """ONNX chord classifier adapter behind the AnalyzerEngine contract."""

    def __init__(
        self,
        *,
        model_path: Path,
        config_path: Path,
        feature_extractor: FeatureExtractor = extract_log_cqt,
    ) -> None:
        self.model_path = Path(model_path)
        self.config_path = Path(config_path)
        self.feature_extractor = feature_extractor

        if not self.model_path.is_file():
            raise OnnxModelError(f"Chord model not found: {self.model_path}")
        if not self.config_path.is_file():
            raise OnnxModelError(f"Chord model config not found: {self.config_path}")

        try:
            config = json.loads(self.config_path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            raise OnnxModelError(f"Could not read chord model config: {exc}") from exc
        if not isinstance(config, dict):
            raise OnnxModelError("Chord model config must be a JSON object")

        self.labels = _labels_from_config(config)
        self.sample_rate = int(_lookup(config, ("sampleRate", "sample_rate", "song_hz"), CQT_SAMPLE_RATE))
        self.hop_length = int(_lookup(config, ("hopLength", "hop_length"), CQT_HOP_LENGTH))
        self.n_bins = int(_lookup(config, ("nBins", "n_bins"), CQT_N_BINS))
        self.sequence_length = int(_lookup(config, ("sequenceLength", "sequence_length", "seq_len", "timestep"), 108))
        self.batch_windows = int(_lookup(config, ("batchWindows", "batch_windows", "batch_size"), 16))
        self.smoothing_frames = int(_lookup(config, ("smoothingFrames", "smoothing_frames", "kernel_size"), 9))
        config_version = str(_lookup(config, ("version", "modelVersion", "model_version"), "unknown"))

        if (
            self.sample_rate != CQT_SAMPLE_RATE
            or self.hop_length != CQT_HOP_LENGTH
            or self.n_bins != CQT_N_BINS
            or self.sequence_length != 108
            or self.batch_windows != 16
        ):
            raise OnnxModelError(
                "Chord model config is incompatible with the pinned 22050/2048/144, 16x108 feature contract"
            )

        try:
            self.session = ort.InferenceSession(str(self.model_path), providers=["CPUExecutionProvider"])
        except Exception as exc:  # onnxruntime exposes multiple provider-specific exception types
            raise OnnxModelError(f"Could not load chord ONNX model: {exc}") from exc

        inputs = self.session.get_inputs()
        outputs = self.session.get_outputs()
        if not inputs or not outputs:
            raise OnnxModelError("Chord ONNX model has no usable input/output")
        self.input_name = str(_lookup(config, ("inputName", "input_name"), inputs[0].name))
        self.output_name = str(_lookup(config, ("outputName", "output_name"), outputs[0].name))

        input_meta = next((item for item in inputs if item.name == self.input_name), None)
        if input_meta is None:
            raise OnnxModelError(f"Configured model input '{self.input_name}' does not exist")
        expected_shape = [self.batch_windows, self.sequence_length, self.n_bins]
        if list(input_meta.shape) != expected_shape:
            raise OnnxModelError(
                f"Chord ONNX input shape must be {expected_shape}, got {list(input_meta.shape)}"
            )

        digest = hashlib.sha256(self.model_path.read_bytes()).hexdigest()[:12]
        self.engine_version = f"onnx:{config_version}:{digest}"

    def analyze(
        self,
        samples: NDArray[np.float32],
        sample_rate: int,
    ) -> FrameChordEvidence:
        if sample_rate != self.sample_rate:
            raise OnnxModelError(f"Chord model requires {self.sample_rate} Hz audio")
        if samples.ndim != 1:
            raise OnnxModelError("Chord model requires mono audio")
        if samples.size == 0:
            return FrameChordEvidence(frames=(), engine_version=self.engine_version)

        features = np.asarray(self.feature_extractor(samples, sample_rate), dtype=np.float32)
        if features.ndim != 2 or features.shape[1] != self.n_bins:
            raise OnnxModelError(
                f"Chord feature extractor must return [frames, {self.n_bins}], got {features.shape}"
            )
        if features.shape[0] == 0:
            return FrameChordEvidence(frames=(), engine_version=self.engine_version)

        max_audio_frames = max(1, int(math.floor(samples.size / self.hop_length)))
        features = features[:max_audio_frames]
        original_frames = int(features.shape[0])
        if original_frames == 0:
            return FrameChordEvidence(frames=(), engine_version=self.engine_version)

        pad_frames = (-original_frames) % self.sequence_length
        if pad_frames:
            features = np.pad(features, ((0, pad_frames), (0, 0)), mode="constant")
        windows = features.reshape(-1, self.sequence_length, self.n_bins)

        logits_parts: list[NDArray[np.float32]] = []
        for batch_start in range(0, windows.shape[0], self.batch_windows):
            real_batch = windows[batch_start : batch_start + self.batch_windows]
            real_count = int(real_batch.shape[0])
            if real_count < self.batch_windows:
                real_batch = np.pad(
                    real_batch,
                    ((0, self.batch_windows - real_count), (0, 0), (0, 0)),
                    mode="constant",
                )
            try:
                output = self.session.run(
                    [self.output_name],
                    {self.input_name: real_batch.astype(np.float32, copy=False)},
                )[0]
            except Exception as exc:
                raise OnnxModelError(f"Chord ONNX inference failed: {exc}") from exc
            output = np.asarray(output, dtype=np.float32)
            expected_prefix = (self.batch_windows, self.sequence_length)
            if output.ndim != 3 or tuple(output.shape[:2]) != expected_prefix:
                raise OnnxModelError(
                    f"Chord ONNX output must start with {expected_prefix}, got {output.shape}"
                )
            if output.shape[2] != len(self.labels):
                raise OnnxModelError(
                    f"Chord ONNX class count {output.shape[2]} does not match label vocabulary {len(self.labels)}"
                )
            logits_parts.append(output[:real_count].reshape(-1, len(self.labels)))

        logits = np.concatenate(logits_parts, axis=0)[:original_frames]
        logits = _smooth_logits(logits, self.smoothing_frames)
        probabilities = _softmax(logits)
        indices = np.argmax(probabilities, axis=1)
        confidences = probabilities[np.arange(probabilities.shape[0]), indices]
        frame_duration = self.hop_length / float(self.sample_rate)
        audio_duration = samples.size / float(self.sample_rate)

        frames: list[ChordFrame] = []
        for index, (class_index, confidence) in enumerate(zip(indices, confidences, strict=True)):
            start_sec = index * frame_duration
            if start_sec >= audio_duration:
                break
            end_sec = min(audio_duration, (index + 1) * frame_duration)
            start_sample = index * self.hop_length
            end_sample = min(samples.size, start_sample + self.hop_length)
            frames.append(
                ChordFrame(
                    start_sec=start_sec,
                    end_sec=end_sec,
                    chord=self.labels[int(class_index)],
                    confidence=float(confidence),
                    rms_db=_rms_db_for_frame(samples, start_sample, end_sample),
                )
            )

        return FrameChordEvidence(frames=tuple(frames), engine_version=self.engine_version)
