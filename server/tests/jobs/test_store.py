from pathlib import Path

from app.jobs.store import JobStore


def test_get_rejects_non_uuid_job_id_before_filesystem_lookup(tmp_path: Path) -> None:
    store = JobStore(tmp_path / "jobs")
    try:
        outside = tmp_path / "escape" / "job.json"
        outside.parent.mkdir(parents=True)
        outside.write_text('{"status":"complete","result":{"should":"not leak"}}', encoding="utf-8")

        assert store.get("../escape") is None
    finally:
        store.close()
