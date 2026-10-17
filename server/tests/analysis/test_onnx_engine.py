from __future__ import annotations

import json
from pathlib import Path

import numpy as np
import onnx
import pytest
from onnx import TensorProto, helper, numpy_helper

from app.analysis.features import CQT_HOP_LENGTH, extract_log_cqt
from app.analysis.onnx_engine import OnnxChordEngine, OnnxModelError, _labels_from_config


def _write_constant_model(path: Path) -> None:
    input_info = helper.make_tensor_value_info(
        "features", TensorProto.FLOAT, [16, 108, 144]
    )
    output_info = helper.make_tensor_value_info(
        "logits", TensorProto.FLOAT, [16, 108, 3]
    )
    logits = np.zeros((16, 108, 3), dtype=np.float32)
    logits[..., 1] = 5.0
    constant = helper.make_node(
        "Constant",
        inputs=[],
        outputs=["logits"],
        value=numpy_helper.from_array(logits, name="constant_logits"),
    )
    graph = helper.make_graph(
        [constant],
        "dont-fret-test-chordnet",
        [input_info],
        [output_info],
    )
    model = helper.make_model(graph, opset_imports=[helper.make_operatorsetid("", 17)])
    model.ir_version = 10
    onnx.save(model, path)


def _write_config(path: Path) -> None:
    path.write_text(
        json.dumps(
            {
                "inputName": "features",
                "outputName": "logits",
                "labels": ["C", "Am", "N"],
                "sampleRate": 22050,
                "hopLength": 2048,
                "nBins": 144,
                "sequenceLength": 108,
                "batchWindows": 16,
                "smoothingFrames": 9,
                "version": "fixture-v1",
            }
        )
    )


def test_extract_log_cqt_matches_chordmini_feature_contract() -> None:
    sample_rate = 22_050
    time = np.arange(sample_rate * 2, dtype=np.float32) / sample_rate
    samples = (0.25 * np.sin(2 * np.pi * 220 * time)).astype(np.float32)

    features = extract_log_cqt(samples, sample_rate)

    assert features.dtype == np.float32
    assert features.ndim == 2
    assert features.shape[1] == 144
    assert features.shape[0] >= 20
    assert np.isfinite(features).all()
    assert CQT_HOP_LENGTH == 2048


def test_onnx_engine_uses_fixed_window_shape_and_config_label_mapping(tmp_path: Path) -> None:
    model_path = tmp_path / "fixture.onnx"
    config_path = tmp_path / "config.json"
    _write_constant_model(model_path)
    _write_config(config_path)

    def fixture_features(samples: np.ndarray, sample_rate: int) -> np.ndarray:
        assert sample_rate == 22_050
        return np.zeros((120, 144), dtype=np.float32)

    engine = OnnxChordEngine(
        model_path=model_path,
        config_path=config_path,
        feature_extractor=fixture_features,
    )

    evidence = engine.analyze(np.zeros(22_050 * 12, dtype=np.float32), 22_050)

    assert len(evidence.frames) == 120
    assert {frame.chord for frame in evidence.frames} == {"Am"}
    assert all(0.0 < frame.confidence <= 1.0 for frame in evidence.frames)
    assert evidence.engine_version.startswith("onnx:fixture-v1:")


def test_onnx_engine_rejects_missing_model_instead_of_falling_back(tmp_path: Path) -> None:
    config_path = tmp_path / "config.json"
    _write_config(config_path)

    with pytest.raises(OnnxModelError, match="model.*not found"):
        OnnxChordEngine(
            model_path=tmp_path / "missing.onnx",
            config_path=config_path,
        )



def test_chordmini_class_notation_is_guitar_friendly_without_changing_indices() -> None:
    config = {
        "idx_to_chord": {
            "0": "C:maj",
            "1": "A:min",
            "2": "Db:min7",
            "3": "G:hdim7",
            "4": "F:7",
            "5": "N",
        }
    }
    assert _labels_from_config(config) == ("C", "Am", "Dbm7", "Gm7b5", "F7", "N")


def test_unknown_qualities_remain_visible_instead_of_fabricated() -> None:
    assert _labels_from_config({"labels": ["C:experimental", "D:aug", "A#:min"]}) == (
        "C:experimental",
        "Daug",
        "A#m",
    )
