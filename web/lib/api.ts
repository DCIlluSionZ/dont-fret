export type AnalysisMode = "normal" | "hq";

export interface ChordSegment {
  startSec: number;
  endSec: number;
  chord: string;
  confidence: number;
  beatStart: number;
  beatCount: number;
  barStart: number;
  isLowConfidence: boolean;
  rawChord: string;
  correctedChord: string | null;
}

export interface AnalysisResult {
  schemaVersion: "dont-fret.analysis.v1";
  track: { title: string; durationSec: number };
  analysis: {
    sampleRate: number;
    requestedMode?: AnalysisMode;
    mode: AnalysisMode;
    engineVersion: string;
    bpm: number | null;
    key: string | null;
    keyConfidence: number | null;
    beatsPerBar?: number;
    beatTimes?: number[];
    warnings?: string[];
  };
  chords: ChordSegment[];
}

export interface AnalysisJob {
  jobId: string;
  status: "queued" | "running" | "complete" | "failed";
  result: AnalysisResult | null;
  error: string | null;
}

async function decodeResponse(response: Response): Promise<unknown> {
  let data: unknown;
  try {
    data = await response.json();
  } catch {
    throw new Error("The analysis server returned an unreadable response.");
  }
  if (!response.ok) {
    let reason = "Analysis server rejected the request.";
    if (data && typeof data === "object" && "detail" in data) {
      const detail = (data as { detail?: unknown }).detail;
      if (typeof detail === "string") reason = detail;
    }
    throw new Error(reason);
  }
  return data;
}

export async function uploadTrack(
  file: File,
  mode: AnalysisMode,
  signal?: AbortSignal,
): Promise<string> {
  const data = new FormData();
  data.set("file", file, file.name);
  data.set("mode", mode);
  const response = await fetch("/analysis-api/analyze", {
    method: "POST",
    body: data,
    signal,
  });
  const payload = await decodeResponse(response);
  if (!payload || typeof payload !== "object" || !("jobId" in payload)) {
    throw new Error("The analysis server did not return a job ID.");
  }
  const jobId = (payload as { jobId: unknown }).jobId;
  if (typeof jobId !== "string" || jobId.length === 0) {
    throw new Error("The analysis server returned an invalid job ID.");
  }
  return jobId;
}

export async function fetchJob(jobId: string, signal?: AbortSignal): Promise<AnalysisJob> {
  const response = await fetch("/analysis-api/jobs/" + encodeURIComponent(jobId), {
    cache: "no-store",
    signal,
  });
  const payload = await decodeResponse(response);
  if (!payload || typeof payload !== "object" || !("status" in payload)) {
    throw new Error("The analysis server returned an invalid job status.");
  }
  return payload as AnalysisJob;
}
