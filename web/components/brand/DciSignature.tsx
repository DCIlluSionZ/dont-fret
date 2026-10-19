import Image from "next/image";
import styles from "./DciSignature.module.css";

type DciSignatureProps = {
  compact?: boolean;
};

/** DCI brand from the user's AI Studio design, independent of the audio engine. */
export function DciSignature({ compact = false }: DciSignatureProps) {
  return (
    <div className={[styles.signature, compact && styles.compact].filter(Boolean).join(" ")} data-testid="dci-signature">
      <Image className={styles.musicMark} src="/dci-music-mark.svg" width={44} height={44} alt="" aria-hidden="true" />
      <div className={styles.wordmarkBlock}>
        <div className={styles.letters} role="img" aria-label="DCI">
          <span className={styles.blue} aria-hidden="true">D</span>
          <span className={styles.indigo} aria-hidden="true">C</span>
          <span className={styles.purple} aria-hidden="true">I</span>
        </div>
        <div className={styles.underline} aria-hidden="true" />
        <p className={styles.catchphrase}>Designed and coded with intelligence.</p>
      </div>
    </div>
  );
}
