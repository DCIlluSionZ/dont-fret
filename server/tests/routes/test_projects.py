from __future__ import annotations

from pathlib import Path

from fastapi.testclient import TestClient

from app.analysis.mock_engine import MockAnalyzerEngine
from app.main import create_app


def _completed_result() -> dict:
    return {
        "schemaVersion": "dont-fret.analysis.v1",
        "track": {"title": "Demo", "durationSec": 2.0},
        "analysis": {"sampleRate": 22050, "mode": "normal", "engineVersion": "fixture-v1"},
        "chords": [
            {
                "startSec": 0,
                "endSec": 2,
                "chord": "Bb",
                "rawChord": "Bb",
                "correctedChord": None,
                "confidence": 0.89,
                "beatStart": 0,
                "beatCount": 4,
                "barStart": 0,
                "isLowConfidence": False,
            }
        ],
    }


def test_saved_project_keeps_classifier_output_unchanged_after_correction(tmp_path: Path) -> None:
    app = create_app(
        analysis_engine=MockAnalyzerEngine(labels=("Bb",)),
        job_root=tmp_path / "jobs",
    )
    with TestClient(app) as client:
        job_id = app.state.job_store.create("demo.wav")
        app.state.job_store.complete(job_id, _completed_result())

        create = client.post("/api/projects", json={"jobId": job_id})
        assert create.status_code == 201
        project_id = create.json()["projectId"]

        patch = client.patch(
            f"/api/projects/{project_id}/corrections",
            json={"corrections": {"0": "Bb7"}},
        )
        assert patch.status_code == 200

        saved = client.get(f"/api/projects/{project_id}")
        assert saved.status_code == 200
        project = saved.json()
        assert project["corrections"] == {"0": "Bb7"}
        assert project["analysisResult"]["chords"][0]["chord"] == "Bb"
        assert project["analysisResult"]["chords"][0]["rawChord"] == "Bb"

    reopened = create_app(job_root=tmp_path / "jobs")
    with TestClient(reopened) as client:
        response = client.get(f"/api/projects/{project_id}")
        assert response.status_code == 200
        assert response.json()["corrections"] == {"0": "Bb7"}


def test_project_endpoint_rejects_invalid_correction_index_or_chord(tmp_path: Path) -> None:
    app = create_app(
        analysis_engine=MockAnalyzerEngine(labels=("Bb",)),
        job_root=tmp_path / "jobs",
    )
    with TestClient(app) as client:
        job_id = app.state.job_store.create("demo.wav")
        app.state.job_store.complete(job_id, _completed_result())
        project_id = client.post("/api/projects", json={"jobId": job_id}).json()["projectId"]
        out_of_range = client.patch(
            f"/api/projects/{project_id}/corrections",
            json={"corrections": {"20": "C"}},
        )
        assert out_of_range.status_code == 422
        invalid_label = client.patch(
            f"/api/projects/{project_id}/corrections",
            json={"corrections": {"0": "><script"}},
        )
        assert invalid_label.status_code == 422
        assert client.get(f"/api/projects/{project_id}").json()["corrections"] == {}


def test_project_creation_rejects_unknown_job(tmp_path: Path) -> None:
    app = create_app(job_root=tmp_path / "jobs")
    with TestClient(app) as client:
        assert client.post("/api/projects", json={"jobId": "none"}).status_code == 404
