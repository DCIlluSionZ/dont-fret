from __future__ import annotations

import json
import threading
import uuid
from concurrent.futures import ThreadPoolExecutor
from copy import deepcopy
from pathlib import Path
from typing import Any, Callable


class JobQueueFull(RuntimeError):
    """Raised when the bounded analysis queue has no capacity."""


class JobStore:
    def __init__(
        self,
        root: Path,
        *,
        max_workers: int = 2,
        max_queue: int = 4,
    ) -> None:
        if max_workers <= 0 or max_queue < 0:
            raise ValueError("invalid job executor capacity")
        self.root = Path(root)
        self.root.mkdir(parents=True, exist_ok=True)
        self._records: dict[str, dict[str, Any]] = {}
        self._lock = threading.RLock()
        self._executor = ThreadPoolExecutor(max_workers=max_workers, thread_name_prefix="dont-fret-analysis")
        self._capacity = threading.BoundedSemaphore(max_workers + max_queue)

    def create(self, filename: str) -> str:
        job_id = str(uuid.uuid4())
        record = {
            "jobId": job_id,
            "status": "queued",
            "filename": filename,
            "result": None,
            "error": None,
        }
        with self._lock:
            self._records[job_id] = record
            self.job_dir(job_id).mkdir(parents=True, exist_ok=True)
            self._persist_locked(job_id)
        return job_id

    def job_dir(self, job_id: str) -> Path:
        return self.root / job_id

    def get(self, job_id: str) -> dict[str, Any] | None:
        with self._lock:
            record = self._records.get(job_id)
            if record is not None:
                return deepcopy(record)
            path = self.job_dir(job_id) / "job.json"
            if not path.is_file():
                return None
            try:
                loaded = json.loads(path.read_text(encoding="utf-8"))
            except (OSError, json.JSONDecodeError):
                return None
            if not isinstance(loaded, dict):
                return None
            self._records[job_id] = loaded
            return deepcopy(loaded)

    def mark_running(self, job_id: str) -> None:
        self._update(job_id, status="running", error=None)

    def complete(self, job_id: str, result: dict[str, Any]) -> None:
        self._update(job_id, status="complete", result=result, error=None)

    def fail(self, job_id: str, error: str) -> None:
        message = error.strip() or "Analysis failed"
        self._update(job_id, status="failed", result=None, error=message)

    def submit(self, job_id: str, work: Callable[[], None]) -> None:
        if not self._capacity.acquire(blocking=False):
            raise JobQueueFull("analysis queue is full")

        def wrapped() -> None:
            try:
                work()
            finally:
                self._capacity.release()

        try:
            self._executor.submit(wrapped)
        except BaseException:
            self._capacity.release()
            raise

    def close(self) -> None:
        self._executor.shutdown(wait=True, cancel_futures=False)

    def _update(self, job_id: str, **changes: Any) -> None:
        with self._lock:
            record = self._records.get(job_id)
            if record is None:
                raise KeyError(job_id)
            record.update(changes)
            self._persist_locked(job_id)

    def _persist_locked(self, job_id: str) -> None:
        target = self.job_dir(job_id) / "job.json"
        temp = target.with_suffix(".json.tmp")
        temp.write_text(
            json.dumps(self._records[job_id], ensure_ascii=False, separators=(",", ":")),
            encoding="utf-8",
        )
        temp.replace(target)
