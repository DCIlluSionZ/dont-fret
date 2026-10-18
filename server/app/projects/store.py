from __future__ import annotations

import json
import os
import re
import threading
import uuid
from copy import deepcopy
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

_CHORD_LABEL = re.compile(r"^(?:N|[A-G](?:#|b)?[A-Za-z0-9()+#b°ø-]*(?:/[A-G](?:#|b)?)?)$")


def _valid_id(value: str) -> bool:
    try:
        return str(uuid.UUID(value)) == value
    except (TypeError, ValueError, AttributeError):
        return False


class ProjectStore:
    """Atomic, durable snapshots with non-destructive correction overlays.

    This is a single-user local storage boundary. An authenticated user/project
    owner model is required before enabling publicly shared projects.
    """

    def __init__(self, root: Path) -> None:
        self.root = Path(root)
        self.root.mkdir(parents=True, exist_ok=True)
        self._lock = threading.RLock()

    def _path(self, project_id: str) -> Path:
        if not _valid_id(project_id):
            raise ValueError("invalid project id")
        return self.root / (project_id + ".json")

    @staticmethod
    def _stamp() -> str:
        return datetime.now(timezone.utc).isoformat()

    def create(self, job_id: str, analysis_result: dict[str, Any]) -> dict[str, Any]:
        if not isinstance(analysis_result, dict) or not isinstance(analysis_result.get("chords"), list):
            raise ValueError("analysis result is missing chords")
        project_id = str(uuid.uuid4())
        record: dict[str, Any] = {
            "projectId": project_id,
            "sourceJobId": job_id,
            "schemaVersion": "dont-fret.project.v1",
            "createdAt": self._stamp(),
            "updatedAt": self._stamp(),
            "analysisResult": deepcopy(analysis_result),
            "corrections": {},
        }
        with self._lock:
            self._write(project_id, record)
        return deepcopy(record)

    def get(self, project_id: str) -> dict[str, Any] | None:
        if not _valid_id(project_id):
            return None
        with self._lock:
            path = self._path(project_id)
            if not path.is_file():
                return None
            try:
                data = json.loads(path.read_text(encoding="utf-8"))
            except (OSError, json.JSONDecodeError):
                return None
            if not isinstance(data, dict) or data.get("projectId") != project_id:
                return None
            return deepcopy(data)

    def update_corrections(self, project_id: str, corrections: dict[str, str]) -> dict[str, Any]:
        with self._lock:
            record = self.get(project_id)
            if record is None:
                raise KeyError(project_id)
            if not isinstance(corrections, dict):
                raise ValueError("corrections must be an object")
            chords = record["analysisResult"]["chords"]
            if len(corrections) > len(chords):
                raise ValueError("too many corrections")
            for index, label in corrections.items():
                if not isinstance(index, str) or not index.isdecimal() or str(int(index)) != index:
                    raise ValueError("correction index must be a canonical non-negative integer")
                if int(index) >= len(chords):
                    raise ValueError("correction index is outside this chord sheet")
                if not isinstance(label, str) or len(label) > 32 or not _CHORD_LABEL.fullmatch(label):
                    raise ValueError("invalid chord label")
            record["corrections"].update(corrections)
            record["updatedAt"] = self._stamp()
            self._write(project_id, record)
            return deepcopy(record)

    def _write(self, project_id: str, record: dict[str, Any]) -> None:
        target = self._path(project_id)
        temporary = target.with_suffix(".json.tmp")
        try:
            fd = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
            with os.fdopen(fd, "w", encoding="utf-8") as stream:
                json.dump(record, stream, ensure_ascii=False, separators=(",", ":"))
            temporary.replace(target)
        finally:
            temporary.unlink(missing_ok=True)
