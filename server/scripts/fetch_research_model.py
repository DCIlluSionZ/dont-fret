#!/usr/bin/env python3
"""Opt-in hash-verified model download from immutable verified Hub revision. Research only."""
from __future__ import annotations

import argparse
import hashlib
import os
import tempfile
import urllib.request
from pathlib import Path

FILES = {
    "chordnet.onnx": ("1faee8e1cf168300afe0f47517e76836ac200c3841b0fb430fad7dfea5ad6394", 17080550),
    "config.json": ("1f26c11ebea51ec08f12e813eb213a729fa0ecc407ac7632dfdc7bad67e65aa4", 3009),
}
BASE_URL = "https://huggingface.co/musetric/chordmini-onnx/resolve/8ab85fc0f61439900cd07ac21257a43eac57075c/"


def fetch_model_file(root: Path, name: str, expected_hash: str, expected_size: int) -> None:
    destination = root / name
    if destination.is_file() and hashlib.sha256(destination.read_bytes()).hexdigest() == expected_hash:
        print("Verified existing", destination)
        return

    request = urllib.request.Request(
        BASE_URL + name + "?download=true",
        headers={"User-Agent": "DontFret-ResearchModelInstaller/1.0"},
    )
    digest = hashlib.sha256()
    size = 0
    handle, temp_path = tempfile.mkstemp(prefix=".dont-fret-model-", dir=root)
    temp = Path(temp_path)
    try:
        with os.fdopen(handle, "wb") as output, urllib.request.urlopen(request, timeout=90) as source:
            while block := source.read(1024 * 1024):
                output.write(block)
                size += len(block)
                digest.update(block)
        if size != expected_size or digest.hexdigest() != expected_hash:
            raise ValueError(
                f"Model artifact rejected for {name}: expected {expected_size} bytes / "
                f"{expected_hash}, received {size} bytes / {digest.hexdigest()}. "
                "No artifact was installed."
            )
        temp.replace(destination)
        destination.chmod(0o600)
        print("Verified and saved", destination)
    finally:
        temp.unlink(missing_ok=True)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--destination", type=Path, default=Path("models"))
    parser.add_argument("--i-understand-provenance-unknown", action="store_true")
    args = parser.parse_args()
    if not args.i_understand_provenance_unknown:
        parser.error("Research-only model. Read the model card, then pass --i-understand-provenance-unknown")
    destination = args.destination.expanduser().resolve()
    destination.mkdir(parents=True, exist_ok=True)
    for name, (digest, size) in FILES.items():
        fetch_model_file(destination, name, digest, size)


if __name__ == "__main__":
    main()
