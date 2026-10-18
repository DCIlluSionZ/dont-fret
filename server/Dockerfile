FROM python:3.12-slim
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 DONT_FRET_JOB_ROOT=/data/jobs
RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg ca-certificates && rm -rf /var/lib/apt/lists/*
WORKDIR /service
COPY server/ /service/
RUN pip install --no-cache-dir . && useradd --uid 10001 --create-home --shell /usr/sbin/nologin dontfret && mkdir -p /data/jobs && chown -R dontfret:dontfret /data/jobs
USER dontfret
VOLUME ["/data/jobs"]
EXPOSE 8000
HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8000/api/health',timeout=3).read()"
CMD ["uvicorn","app.main:app","--host","0.0.0.0","--port","8000"]
