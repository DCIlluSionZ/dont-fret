from __future__ import annotations

import io
import time
import wave
from pathlib import Path

from fastapi.testclient import TestClient

from app.analysis.mock_engine import MockAnalyzerEngine
from app.hq.separation import StemSeparationError
from app.main import create_app


class FailingSeparator:
    def separate(self, source_path: Path, output_dir: Path):
        raise StemSeparationError("fixture separation failed")


def _wav_bytes(duration_sec: float = 1.0, sample_rate: int = 44_100) -> bytes:
    buffer = io.BytesIO()
    with wave.open(buffer, "wb") as wav:
        wav.setnchannels(2)
        wav.setsampwidth(2)
        wav.setframerate(sample_rate)
        wav.writeframes(b"\x00\x00\x00\x00" * int(duration_sec * sample_rate))
    return buffer.getvalue()


def _wait(client: TestClient, job_id: str) -> dict:
    deadline = time.monotonic() + 8.0
    while time.monotonic() < deadline:
        job = client.get(f"/api/jobs/{job_id}").json()
        if job["status"] in {"complete", "failed"}:
            return job
        time.sleep(0.03)
    raise AssertionError("job did not finish")


def test_hq_separation_failure_preserves_normal_result_and_returns_warning(tmp_path: Path) -> None:
    app = create_app(
        analysis_engine=MockAnalyzerEngine(labels=("C", "Am", "F", "G")),
        stem_separator=FailingSeparator(),
        job_root=tmp_path / "jobs",
    )
    with TestClient(app) as client:
        accepted = client.post(
            "/api/analyze",
            data={"mode": "hq"},
            files={"file": ("demo.wav", _wav_bytes(), "audio/wav")},
        )
        assert accepted.status_code == 202
        job = _wait(client, accepted.json()["jobId"])

    assert job["status"] == "complete"
    assert job["result"]["analysis"]["requestedMode"] == "hq"
    assert job["result"]["analysis"]["mode"] == "normal"
    assert any("fixture separation failed" in warning for warning in job["result"]["analysis"]["warnings"])
    assert job["result"]["chords"]
