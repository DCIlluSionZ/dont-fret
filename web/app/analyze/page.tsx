"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { AudioDropzone } from "../../components/upload/AudioDropzone";
import { FollowPlayer } from "../../components/player/FollowPlayer";
import { ChordSheet } from "../../components/sheet/ChordSheet";
import { createProject, fetchJob, getProject, saveCorrections, uploadTrack } from "../../lib/api";
import { exportAnalysisJson, exportChordSheetText, exportChordsCsv } from "../../lib/exports";
import type { AnalysisMode, AnalysisResult } from "../../lib/api";
import { rankCapoOptions } from "../../lib/music/capo";
import { transposeChord } from "../../lib/music/chords";
import "./analyze.css";

type Phase = "idle" | "uploading" | "analysing" | "complete" | "failed";
type View = "sheet" | "follow";

const POLL_INTERVAL_MS = 900;
const MAX_POLLS = 400;

function pause(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function soundingLabel(result: AnalysisResult, index: number, corrections: Record<number, string>): string {
  return corrections[index] ?? result.chords[index].correctedChord ?? result.chords[index].chord;
}

export default function AnalyzePage() {
  const [file, setFile] = useState<File | null>(null);
  const [mode, setMode] = useState<AnalysisMode>("normal");
  const [phase, setPhase] = useState<Phase>("idle");
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<AnalysisResult | null>(null);
  const [jobId, setJobId] = useState<string | null>(null);
  const [projectId, setProjectId] = useState<string | null>(null);
  const [saveState, setSaveState] = useState<"idle" | "saving" | "saved" | "error">("idle");
  const [corrections, setCorrections] = useState<Record<number, string>>({});
  const [easy, setEasy] = useState(false);
  const [view, setView] = useState<View>("sheet");
  const [position, setPosition] = useState(0);
  const [editing, setEditing] = useState<number | null>(null);
  const [editText, setEditText] = useState("");
  const [audioUrl, setAudioUrl] = useState<string | null>(null);
  const abortRef = useRef<AbortController | null>(null);

  useEffect(() => {
    if (!file) {
      setAudioUrl(null);
      return;
    }
    const url = URL.createObjectURL(file);
    setAudioUrl(url);
    return () => URL.revokeObjectURL(url);
  }, [file]);

  useEffect(() => () => abortRef.current?.abort(), []);

  useEffect(() => {
    const savedId = new URLSearchParams(window.location.search).get("project");
    if (!savedId) return;
    const controller = new AbortController();
    getProject(savedId, controller.signal)
      .then((saved) => {
        if (controller.signal.aborted) return;
        setResult(saved.analysisResult);
        setJobId(saved.sourceJobId);
        setProjectId(saved.projectId);
        setCorrections(saved.corrections);
        setFile(null);
        setPhase("complete");
        setSaveState("saved");
      })
      .catch((reason: unknown) => {
        if (controller.signal.aborted) return;
        setError(reason instanceof Error ? reason.message : "Could not open saved project.");
        setPhase("failed");
      });
    return () => controller.abort();
  }, []);

  const rawChords = useMemo(
    () => result?.chords.map((_, index) => soundingLabel(result, index, corrections)) ?? [],
    [result, corrections],
  );
  const recommendedCapo = useMemo(
    () => rankCapoOptions(rawChords.filter((chord) => chord !== "N"))[0] ?? null,
    [rawChords],
  );
  const displayChords = useMemo(
    () => rawChords.map((chord) => easy && recommendedCapo ? transposeChord(chord, -recommendedCapo.capoFret) : chord),
    [rawChords, easy, recommendedCapo],
  );
  const activeIndex = result?.chords.findIndex((chord) => position >= chord.startSec && position < chord.endSec) ?? -1;
  const currentChord = activeIndex >= 0 ? displayChords[activeIndex] : "N";
  const currentBar = activeIndex >= 0 && result ? result.chords[activeIndex].barStart + 1 : null;

  function chooseFile(next: File | null) {
    abortRef.current?.abort();
    setFile(next);
    setJobId(null);
    setProjectId(null);
    setSaveState("idle");
    if (window.location.search) window.history.replaceState(null, "", "/analyze");
    setResult(null);
    setCorrections({});
    setPosition(0);
    setPhase("idle");
    setError(null);
    setEditing(null);
  }

  async function analyse() {
    if (!file) return;
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;
    setPhase("uploading");
    setResult(null);
    setJobId(null);
    setProjectId(null);
    setSaveState("idle");
    setError(null);
    setCorrections({});
    setPosition(0);
    setEasy(false);
    setView("sheet");
    try {
      const nextJobId = await uploadTrack(file, mode, controller.signal);
      if (controller.signal.aborted) return;
      setJobId(nextJobId);
      setPhase("analysing");
      for (let count = 0; count < MAX_POLLS; count += 1) {
        await pause(POLL_INTERVAL_MS);
        if (controller.signal.aborted) return;
        const job = await fetchJob(nextJobId, controller.signal);
        if (job.status === "failed") {
          throw new Error(job.error || "Analysis failed without details.");
        }
        if (job.status === "complete") {
          if (!job.result || job.result.schemaVersion !== "dont-fret.analysis.v1" || !Array.isArray(job.result.chords)) {
            throw new Error("The analysis result did not match the expected chord schema.");
          }
          setResult(job.result);
          setPhase("complete");
          return;
        }
      }
      throw new Error("Analysis is taking longer than this session allows. Please retry.");
    } catch (cause) {
      if (!controller.signal.aborted) {
        setError(cause instanceof Error ? cause.message : "The song could not be analysed.");
        setPhase("failed");
      }
    }
  }

  function startEdit(index: number) {
    if (!result) return;
    setEditing(index);
    setEditText(soundingLabel(result, index, corrections));
  }

  function saveEdit() {
    if (editing === null) return;
    const next = editText.trim();
    if (!next || next.length > 32) return;
    setCorrections((previous) => ({ ...previous, [editing]: next }));
    setSaveState("idle");
    setEditing(null);
  }

  async function saveProject() {
    if (!result || !jobId) return;
    setSaveState("saving");
    try {
      const id = projectId ?? (await createProject(jobId)).projectId;
      setProjectId(id);
      await saveCorrections(id, corrections);
      window.history.replaceState(null, "", "/analyze?project=" + encodeURIComponent(id));
      setSaveState("saved");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Could not save the project.");
      setSaveState("error");
    }
  }

  function download(format: "json" | "csv" | "txt") {
    if (!result) return;
    const content = format === "json"
      ? exportAnalysisJson(result, corrections)
      : format === "csv"
        ? exportChordsCsv(result, corrections)
        : exportChordSheetText(
            result,
            corrections,
            easy && recommendedCapo ? { capoFret: recommendedCapo.capoFret } : {},
          );
    const mime = format === "json" ? "application/json" : format === "csv" ? "text/csv" : "text/plain";
    const name = result.track.title.replace(/[^a-zA-Z0-9_.-]+/g, "_").slice(0, 70) || "song";
    const url = URL.createObjectURL(new Blob([content], { type: mime + ";charset=utf-8" }));
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = name + "_dont-fret." + format;
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    window.setTimeout(() => URL.revokeObjectURL(url), 1_000);
  }

  const busy = phase === "uploading" || phase === "analysing";

  return (
    <main className="df-workspace">
      <header className="workspace-nav">
        <a className="brand" href="/">DON&apos;T <span>FRET</span><small> / STUDIO</small></a>
        <span>Hear it · Shape it · Play it</span>
      </header>
      <div className="workspace-intro">
        <div className="step-eyebrow">YOUR SONG / YOUR SHEET</div>
        <h1>Analyse a song</h1>
        <p>Real audio in. A readable guitar chord sheet out. No waveform maze.</p>
      </div>
      <div className="workspace-layout">
        <AudioDropzone
          file={file}
          mode={mode}
          busy={busy}
          onFile={chooseFile}
          onMode={setMode}
          onSubmit={() => { void analyse(); }}
        />
        <div className="result-column">
          {busy && (
            <div className="status-panel" role="status">
              <div className="pulse-dot" aria-hidden="true" />
              <h2>{phase === "uploading" ? "Uploading audio…" : "Analysing your song…"}</h2>
              <p>Decoding, following the beat and checking the harmony. We&apos;ll flag uncertain changes.</p>
            </div>
          )}
          {phase === "failed" && (
            <div className="error-panel" role="alert">
              <h2>Couldn&apos;t analyse this song</h2>
              <p>{error}</p>
              <button className="secondary-action" type="button" onClick={() => void analyse()}>Try again</button>
            </div>
          )}
          {phase === "idle" && (
            <section className="empty-result">
              <div className="empty-note" aria-hidden="true">♬</div>
              <h2>The sheet is yours to shape.</h2>
              <p>Choose a recording to reveal timed chords, confidence markers and capo-friendly guitar shapes.</p>
              <div className="placeholder-bars" aria-hidden="true"><i /><i /><i /><i /></div>
            </section>
          )}
          {phase === "complete" && result && (
            <>
              <div className="result-toolbar">
                <div className="segmented" aria-label="Chord display">
                  <button type="button" className={!easy ? "selected" : ""} aria-pressed={!easy} onClick={() => setEasy(false)}>Original</button>
                  <button type="button" className={easy ? "selected" : ""} aria-pressed={easy} onClick={() => setEasy(true)}>Easy Guitar</button>
                </div>
                <div className="segmented" aria-label="Sheet navigation">
                  <button type="button" className={view === "sheet" ? "selected" : ""} aria-pressed={view === "sheet"} onClick={() => setView("sheet")}>Sheet</button>
                  <button type="button" className={view === "follow" ? "selected" : ""} aria-pressed={view === "follow"} onClick={() => setView("follow")}>Follow</button>
                </div>
                <button className="print-action" type="button" onClick={() => window.print()}>Print sheet</button>
              </div>
              <div className="export-toolbar">
                <button className="save-action" type="button" onClick={() => void saveProject()} disabled={!jobId || saveState === "saving"}>
                  {saveState === "saving" ? "Saving…" : saveState === "saved" ? "Saved project" : "Save project"}
                </button>
                <span>Export:</span>
                <button type="button" onClick={() => download("json")}>JSON</button>
                <button type="button" onClick={() => download("csv")}>CSV</button>
                <button type="button" onClick={() => download("txt")}>Text sheet</button>
              </div>
              {saveState === "saved" && <p className="saved-note">Project saved. This URL reopens the corrected sheet; upload the audio again for playback.</p>}
              {saveState === "error" && <p className="result-warning" role="alert">{error}</p>}
              {easy && recommendedCapo && (
                <p className="capo-hint">Capo {recommendedCapo.capoFret} · Play these shapes to keep the original sounding key.</p>
              )}
              {(result.analysis.warnings ?? []).map((warning, index) => <p className="result-warning" key={index}>{warning}</p>)}
              {view === "follow" && (
                <FollowPlayer
                  audioUrl={audioUrl}
                  position={position}
                  duration={result.track.durationSec}
                  currentChord={currentChord}
                  currentBar={currentBar}
                  onPosition={setPosition}
                />
              )}
              <ChordSheet
                result={result}
                easy={easy}
                capoFret={easy ? recommendedCapo?.capoFret ?? null : null}
                displayChords={displayChords}
                activeIndex={view === "follow" ? activeIndex : -1}
                onEdit={startEdit}
              />
              {editing !== null && (
                <form className="correction-panel" onSubmit={(event) => { event.preventDefault(); saveEdit(); }}>
                  <label htmlFor="chord-correction">Correct chord {editing + 1}</label>
                  <input
                    id="chord-correction"
                    aria-label={"Correct chord " + (editing + 1)}
                    value={editText}
                    maxLength={32}
                    onChange={(event) => setEditText(event.target.value)}
                    autoFocus
                  />
                  <button className="primary-action" type="submit">Save correction</button>
                  <button className="secondary-action" type="button" onClick={() => setEditing(null)}>Cancel</button>
                  <small>Corrections update this sheet immediately. Select Save project to preserve them.</small>
                </form>
              )}
            </>
          )}
        </div>
      </div>
    </main>
  );
}
