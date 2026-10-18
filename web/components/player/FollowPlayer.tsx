"use client";

import { useRef } from "react";

interface Props {
  audioUrl: string | null;
  position: number;
  duration: number;
  currentChord: string;
  currentBar: number | null;
  onPosition: (position: number) => void;
}

export function FollowPlayer({
  audioUrl,
  position,
  duration,
  currentChord,
  currentBar,
  onPosition,
}: Props) {
  const audio = useRef<HTMLAudioElement | null>(null);

  function seek(value: number) {
    const safe = Math.max(0, Math.min(duration, Number.isFinite(value) ? value : 0));
    onPosition(safe);
    if (audio.current && Number.isFinite(audio.current.duration)) {
      audio.current.currentTime = safe;
    }
  }

  return (
    <section className="follow-panel" aria-label="Follow playback controls">
      <div className="follow-readout">
        <span>FOLLOW ALONG</span>
        <strong>Now: {currentChord}</strong>
        <small>{currentBar === null ? "Waiting for first chord" : "Bar " + currentBar}</small>
      </div>
      {audioUrl && (
        <audio
          ref={audio}
          controls
          preload="metadata"
          src={audioUrl}
          onTimeUpdate={(event) => onPosition(event.currentTarget.currentTime)}
          onSeeked={(event) => onPosition(event.currentTarget.currentTime)}
          aria-label="Uploaded song playback"
        />
      )}
      <label className="position-control">
        <span>Playback position</span>
        <input
          aria-label="Playback position"
          type="number"
          min={0}
          max={Math.max(0, duration)}
          step="0.1"
          value={Number.isFinite(position) ? position.toFixed(1) : "0.0"}
          onChange={(event) => seek(Number(event.currentTarget.value))}
        />
        <span className="position-duration">/ {duration.toFixed(1)} sec</span>
      </label>
    </section>
  );
}
