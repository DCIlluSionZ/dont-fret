from __future__ import annotations

import io
import json
import time
import wave
from pathlib import Path

import jsonschema
import pytest
from fastapi.testclient import TestClient

from app.analysis.mock_engine import MockAnalyzerEngine
from app.main import create_app


def _wav_bytes(duration_sec: float = 1.0, sample_rate: int = 44_100) -> bytes:
    buffer = io.BytesIO()
    with wave.open(buffer, "wb") as wav:
        wav.setnchannels(2)
        wav.setsampwidth(2)
        wav.setframerate(sample_rate)
        frame_count = int(duration_sec * sample_rate)
        wav.writeframes(b"\x00\x00\x00\x00" * frame_count)
    return buffer.getvalue()


def _wait_for_terminal_job(client: TestClient, job_id: str, timeout_sec: float = 8.0) -> dict:
    deadline = time.monotonic() + timeout_sec
    last: dict | None = None
    while time.monotonic() < deadline:
        response = client.get(f"/api/jobs/{job_id}")
        assert response.status_code == 200
        last = response.json()
        if last["status"] in {"complete", "failed"}:
            return last
        time.sleep(0.03)
    pytest.fail(f"job did not finish: {last}")


@pytest.fixture
def client(tmp_path: Path):
    test_app = create_app(
        analysis_engine=MockAnalyzerEngine(labels=("C", "Am", "F", "G")),
        job_root=tmp_path / "jobs",
        max_upload_bytes=2 * 1024 * 1024,
    )
    with TestClient(test_app) as active:
        yield active


def test_supported_wav_upload_creates_job_and_returns_completed_schema(client: TestClient) -> None:
    response = client.post(
        "/api/analyze",
        files={"file": ("demo.wav", _wav_bytes(), "audio/wav")},
    )

    assert response.status_code == 202
    accepted = response.json()
    assert accepted["status"] == "queued"
    assert isinstance(accepted["jobId"], str) and accepted["jobId"]

    job = _wait_for_terminal_job(client, accepted["jobId"])
    assert job["status"] == "complete"
    assert job["result"]["schemaVersion"] == "dont-fret.analysis.v1"
    assert job["result"]["analysis"]["sampleRate"] == 22_050
    assert job["result"]["analysis"]["mode"] == "normal"
    assert job["result"]["analysis"]["engineVersion"] == "mock-v1"

    schema_path = Path(__file__).parents[3] / "contracts" / "analysis-result.schema.json"
    schema = json.loads(schema_path.read_text(encoding="utf-8"))
    jsonschema.validate(job["result"], schema)


def test_unsupported_extension_is_rejected_before_job_creation(client: TestClient) -> None:
    response = client.post(
        "/api/analyze",
        files={"file": ("notes.txt", b"not audio", "text/plain")},
    )

    assert response.status_code == 415
    assert "MP3" in response.json()["detail"]


def test_corrupt_supported_file_becomes_failed_job(client: TestClient) -> None:
    response = client.post(
        "/api/analyze",
        files={"file": ("broken.mp3", b"this is not an mp3", "audio/mpeg")},
    )

    assert response.status_code == 202
    job = _wait_for_terminal_job(client, response.json()["jobId"])
    assert job["status"] == "failed"
    assert job["result"] is None
    assert isinstance(job["error"], str) and job["error"]


def test_oversized_file_is_rejected_with_413(tmp_path: Path) -> None:
    test_app = create_app(
        analysis_engine=MockAnalyzerEngine(labels=("C",)),
        job_root=tmp_path / "jobs",
        max_upload_bytes=128,
    )
    with TestClient(test_app) as limited_client:
        response = limited_client.post(
            "/api/analyze",
            files={"file": ("huge.wav", _wav_bytes(), "audio/wav")},
        )

    assert response.status_code == 413


def test_unknown_job_returns_404(client: TestClient) -> None:
    response = client.get("/api/jobs/not-a-real-job")

    assert response.status_code == 404
