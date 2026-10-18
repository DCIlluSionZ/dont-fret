from __future__ import annotations

import os
import tempfile
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Any, AsyncIterator

from fastapi import FastAPI

from app.config import Settings
from app.hq.separation import DemucsCliSeparator, HpssHarmonicSeparator
from app.jobs.store import JobStore
from app.projects.store import ProjectStore
from app.routes.projects import router as projects_router
from app.routes.analyze import DEFAULT_MAX_UPLOAD_BYTES, router as analyze_router


def create_app(
    *,
    analysis_engine: Any | None = None,
    stem_separator: Any | None = None,
    job_root: Path | None = None,
    max_upload_bytes: int = DEFAULT_MAX_UPLOAD_BYTES,
    max_workers: int = 2,
    max_queue: int = 4,
) -> FastAPI:
    if max_upload_bytes <= 0:
        raise ValueError("max_upload_bytes must be positive")

    configured_root = job_root
    if configured_root is None:
        configured_root = Path(
            os.getenv(
                "DONT_FRET_JOB_ROOT",
                str(Path(tempfile.gettempdir()) / "dont-fret-jobs"),
            )
        ).expanduser()
    store = JobStore(configured_root, max_workers=max_workers, max_queue=max_queue)

    configured_separator = stem_separator
    if configured_separator is None:
        settings = Settings.from_env()
        if settings.demucs_command is not None:
            configured_separator = DemucsCliSeparator(
                command=settings.demucs_command,
                model_name=settings.demucs_model,
            )
        else:
            configured_separator = HpssHarmonicSeparator()

    @asynccontextmanager
    async def lifespan(_: FastAPI) -> AsyncIterator[None]:
        yield
        store.close()

    application = FastAPI(
        title="Don't Fret Analysis API",
        version="0.1.0",
        docs_url="/api/docs",
        redoc_url=None,
        lifespan=lifespan,
    )
    application.state.analysis_engine = analysis_engine
    application.state.stem_separator = configured_separator
    application.state.job_store = store
    application.state.project_store = ProjectStore(configured_root / "projects")
    application.state.max_upload_bytes = max_upload_bytes
    application.include_router(analyze_router)
    application.include_router(projects_router)

    @application.get("/api/health")
    def health() -> dict[str, str]:
        return {"status": "ok"}

    return application


app = create_app()
