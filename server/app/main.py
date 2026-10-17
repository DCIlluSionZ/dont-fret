from fastapi import FastAPI

app = FastAPI(
    title="Don't Fret Analysis API",
    version="0.1.0",
    docs_url="/api/docs",
    redoc_url=None,
)


@app.get("/api/health")
def health() -> dict[str, str]:
    return {"status": "ok"}
