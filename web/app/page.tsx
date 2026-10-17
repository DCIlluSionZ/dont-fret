const featureCards = [
  ["Hear the real harmony", "Decoded audio, timed chords, beat-aware smoothing and confidence instead of guessed stock progressions."],
  ["Play it your way", "Keep the concert chords or flip to Easy Guitar for capo-friendly shapes that preserve the original sound."],
  ["One sheet, not a cockpit", "A clean printable chord sheet first, then a Follow view that tracks the song while you play."],
];

export default function HomePage() {
  return (
    <main style={{ minHeight: "100vh", background: "radial-gradient(circle at 18% 12%, #242043 0, #10131d 34%, #090b10 68%)" }}>
      <section style={{ maxWidth: 1080, margin: "0 auto", padding: "72px 24px 56px" }}>
        <div style={{ display: "inline-flex", gap: 8, alignItems: "center", padding: "8px 12px", border: "1px solid #34394a", borderRadius: 999, color: "#d6d9e4", fontSize: 13 }}>
          DCI Studio Tool · Web rebuild
        </div>
        <h1 style={{ fontSize: "clamp(54px, 10vw, 112px)", lineHeight: 0.88, letterSpacing: "-0.07em", margin: "34px 0 24px", maxWidth: 900 }}>
          Don&apos;t Fret.
          <span style={{ display: "block", color: "#b9ff66" }}>Just play it.</span>
        </h1>
        <p style={{ maxWidth: 690, color: "#b7bdca", fontSize: "clamp(18px, 2.2vw, 24px)", lineHeight: 1.45, marginBottom: 34 }}>
          Drop in a song. Get the key, BPM and a timed chord sheet, then switch to guitar-friendly capo shapes without changing what the song actually sounds like.
        </p>
        <a href="/analyze" style={{ display: "inline-block", textDecoration: "none", background: "#b9ff66", color: "#0a0d0c", fontWeight: 800, padding: "15px 22px", borderRadius: 12 }}>
          Analyse a song
        </a>

        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(230px, 1fr))", gap: 14, marginTop: 70 }}>
          {featureCards.map(([title, copy]) => (
            <article key={title} style={{ border: "1px solid #252a37", borderRadius: 18, padding: 22, background: "rgba(17,20,29,.72)" }}>
              <h2 style={{ marginTop: 0, fontSize: 18 }}>{title}</h2>
              <p style={{ color: "#9da4b2", lineHeight: 1.55, marginBottom: 0 }}>{copy}</p>
            </article>
          ))}
        </div>
      </section>
    </main>
  );
}
