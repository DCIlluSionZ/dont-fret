from __future__ import annotations

from typing import Any

from fastapi import APIRouter, HTTPException, Request
from pydantic import BaseModel, Field

from app.jobs.store import JobStore
from app.projects.store import ProjectStore

router = APIRouter()


class CreateProject(BaseModel):
    jobId: str = Field(min_length=1, max_length=128)


class UpdateCorrections(BaseModel):
    corrections: dict[str, str]


@router.post("/api/projects", status_code=201)
def create_project(payload: CreateProject, request: Request) -> dict[str, Any]:
    jobs: JobStore = request.app.state.job_store
    job = jobs.get(payload.jobId)
    if job is None:
        raise HTTPException(status_code=404, detail="Analysis job not found")
    if job.get("status") != "complete" or not isinstance(job.get("result"), dict):
        raise HTTPException(status_code=409, detail="Analysis job is not complete")
    projects: ProjectStore = request.app.state.project_store
    return projects.create(payload.jobId, job["result"])


@router.get("/api/projects/{project_id}")
def get_project(project_id: str, request: Request) -> dict[str, Any]:
    projects: ProjectStore = request.app.state.project_store
    saved = projects.get(project_id)
    if saved is None:
        raise HTTPException(status_code=404, detail="Project not found")
    return saved


@router.patch("/api/projects/{project_id}/corrections")
def update_project(
    project_id: str,
    payload: UpdateCorrections,
    request: Request,
) -> dict[str, Any]:
    projects: ProjectStore = request.app.state.project_store
    try:
        return projects.update_corrections(project_id, payload.corrections)
    except KeyError:
        raise HTTPException(status_code=404, detail="Project not found") from None
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
