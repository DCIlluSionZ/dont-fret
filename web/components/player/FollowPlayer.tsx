"use client";

import { useRef, useState } from "react";

interface Props {
  audioUrl: string | null;
  position: number;
  duration: number;
  currentChord: string;
  currentBar: number | null;
  onPosition: (position: number) => void;
}

const SPEED_OPTIONS = [0.5, 0.75, 1.0, 1.25] as const;

export function FollowPlayer({
  audioUrl,
  position,
  duration,
  currentChord,
  currentBar,
  onPosition,
}: Props) {
  const audio = useRef<HTMLAudioElement | null>(null);
  const [speed, setSpeed] = useState<number>(1.0);
  const [loopStart, setLoopStart] = useState<number | null>(null);
  const [loopEnd, setLoopEnd] = useState<number | null>(null);

  function seek(value: number) {
    const safe = Math.max(0, Math.min(duration, Number.isFinite(value) ? value : 0));
    onPosition(safe);
    if (audio.current && Number.isFinite(audio.current.duration)) {
      audio.current.currentTime = safe;
    }
  }

  function changeSpeed(nextSpeed: number) {
    setSpeed(nextSpeed);
    if (audio.current) {
      audio.current.playbackRate = nextSpeed;
    }
  }

  function handleTimeUpdate(currentTime: number) {
    if (
      loopStart !== null &&
      loopEnd !== null &&
      loopEnd > loopStart + 0.25 &&
      currentTime >= loopEnd
    ) {
      seek(loopStart);
      return;
    }
    onPosition(currentTime);
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
          onTimeUpdate={(event) => handleTimeUpdate(event.currentTarget.currentTime)}
          onSeeked={(event) => onPosition(event.currentTarget.currentTime)}
          aria-label="Uploaded song playback"
        />
      )}
      <div className="follow-practice-controls" aria-label="Practice speed and section loop">
        <div className="segmented" aria-label="Playback speed">
          {SPEED_OPTIONS.map((option) => (
            <button
              key={option}
              type="button"
              className={speed === option ? "selected" : ""}
              aria-pressed={speed === option}
              onClick={() => changeSpeed(option)}
            >
              {option}x
            </button>
          ))}
        </div>
        <div className="segmented" aria-label="Section loop">
          <button type="button" onClick={() => setLoopStart(position)}>
            {loopStart === null ? "Loop A" : `A: ${loopStart.toFixed(1)}s`}
          </button>
          <button
            type="button"
            onClick={() => {
              if (loopStart === null || position > loopStart + 0.25) {
                setLoopEnd(position);
              }
            }}
          >
            {loopEnd === null ? "Loop B" : `B: ${loopEnd.toFixed(1)}s`}
          </button>
          {(loopStart !== null || loopEnd !== null) && (
            <button
              type="button"
              onClick={() => {
                setLoopStart(null);
                setLoopEnd(null);
              }}
            >
              Clear loop
            </button>
          )}
        </div>
      </div>
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
