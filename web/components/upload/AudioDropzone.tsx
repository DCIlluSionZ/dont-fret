"use client";

import type { ChangeEvent } from "react";
import type { AnalysisMode } from "../../lib/api";

interface Props {
  file: File | null;
  mode: AnalysisMode;
  busy: boolean;
  onFile: (file: File | null) => void;
  onMode: (mode: AnalysisMode) => void;
  onSubmit: () => void;
}

export function AudioDropzone({ file, mode, busy, onFile, onMode, onSubmit }: Props) {
  function select(event: ChangeEvent<HTMLInputElement>) {
    onFile(event.currentTarget.files?.[0] ?? null);
  }

  return (
    <section className="upload-card" aria-labelledby="upload-title">
      <div className="step-eyebrow">01 / Bring your song</div>
      <h2 id="upload-title">Every chord starts somewhere.</h2>
      <p>Choose an audio file. We decode it properly before looking for beats or harmony.</p>
      <label className="file-drop">
        <input
          data-testid="audio-input"
          aria-label="Choose audio file"
          type="file"
          accept=".mp3,.m4a,.wav,.flac,audio/*"
          onChange={select}
          disabled={busy}
        />
        <span className="file-glyph" aria-hidden="true">♫</span>
        <span className="file-label">{file ? file.name : "Choose MP3, M4A, WAV or FLAC"}</span>
        <span className="file-subtitle">{file ? (file.size / (1024 * 1024)).toFixed(1) + " MB" : "Tap to browse your files"}</span>
      </label>
      <fieldset className="mode-chooser" disabled={busy}>
        <legend>Analysis quality</legend>
        <label className={mode === "normal" ? "mode-choice active" : "mode-choice"}>
          <input
            type="radio"
            name="quality"
            checked={mode === "normal"}
            onChange={() => onMode("normal")}
          />
          <span><strong>Normal</strong><small>Fast chord scan</small></span>
        </label>
        <label className={mode === "hq" ? "mode-choice active" : "mode-choice"}>
          <input
            type="radio"
            name="quality"
            checked={mode === "hq"}
            onChange={() => onMode("hq")}
          />
          <span><strong>HQ</strong><small>Try harmonic stem separation</small></span>
        </label>
      </fieldset>
      <button className="primary-action" type="button" disabled={!file || busy} onClick={onSubmit}>
        {busy ? "Working on your song…" : "Analyse track"}
      </button>
      <p className="input-note">HQ may fall back to Normal if separation is unavailable. Nothing is labelled as a chord unless the analysis supports it.</p>
    </section>
  );
}
