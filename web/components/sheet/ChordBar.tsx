import type { ChordSegment } from "../../lib/api";

interface Props {
  segment: ChordSegment;
  index: number;
  displayChord: string;
  isActive: boolean;
  onEdit: (index: number) => void;
}

export function ChordBar({ segment, index, displayChord, isActive, onEdit }: Props) {
  const isUnknown = displayChord === "N";
  return (
    <button
      type="button"
      data-testid={"chord-" + index}
      className={"chord-cell" + (isActive ? " current" : "") + (segment.isLowConfidence || isUnknown ? " uncertain" : "")}
      onClick={() => onEdit(index)}
      aria-label={"Edit chord " + (index + 1) + ": " + displayChord}
      title="Click to correct this chord"
    >
      <span className="chord-location">BAR {segment.barStart + 1} · {segment.beatCount} BEATS</span>
      <strong className="chord-symbol">{isUnknown ? "N" : displayChord}</strong>
      <span className="chord-meta">
        {isUnknown ? "No reliable chord" : segment.isLowConfidence ? "Needs review" : Math.round(segment.confidence * 100) + "% confidence"}
      </span>
    </button>
  );
}
