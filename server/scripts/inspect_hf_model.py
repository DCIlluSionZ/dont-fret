#!/usr/bin/env python3
"""Inspect canonical Hugging Face metadata, without installing or trusting weights."""
from __future__ import annotations

import json
import urllib.request

REPO = "musetric/chordmini-onnx"
URL = f"https://huggingface.co/api/models/{REPO}?blobs=true"
SELECTED = ("chordnet.onnx", "config.json", "cqt-plan.bin", "cqt-plan.manifest.json")


def main() -> None:
    request = urllib.request.Request(URL, headers={"User-Agent": "DontFret-ResearchAudit/1.0"})
    with urllib.request.urlopen(request, timeout=30) as response:
        model = json.load(response)
    files = {}
    for entry in model.get("siblings", []):
        path = entry.get("rfilename")
        if path in SELECTED:
            lfs = entry.get("lfs") or {}
            files[path] = {
                "fileName": path,
                "size": entry.get("size"),
                "lfsSha256": lfs.get("sha256"),
                "lfsSize": lfs.get("size"),
                "blobId": entry.get("blobId"),
            }
    result = {
        "source": URL,
        "repoSha": model.get("sha"),
        "lastModified": model.get("lastModified"),
        "files": files,
    }
    print("HUGGING_FACE_ARTIFACT_METADATA=" + json.dumps(result, sort_keys=True))
    if not isinstance(result["repoSha"], str) or len(result["repoSha"]) != 40:
        raise RuntimeError("No immutable model revision was provided by Hugging Face")
    if "chordnet.onnx" not in files or "config.json" not in files:
        raise RuntimeError("Model file metadata is missing")


if __name__ == "__main__":
    main()
