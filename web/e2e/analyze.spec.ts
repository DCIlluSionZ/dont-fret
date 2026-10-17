import { expect, test } from "@playwright/test";

const finishedResult = {
  schemaVersion: "dont-fret.analysis.v1",
  track: { title: "Demo", durationSec: 8 },
  analysis: {
    sampleRate: 22050,
    requestedMode: "normal",
    mode: "normal",
    engineVersion: "fixture-v1",
    bpm: 120,
    key: "Bb major",
    keyConfidence: 0.91,
    beatsPerBar: 4,
    beatTimes: [0, 0.5, 1, 1.5, 2, 2.5, 3, 3.5, 4, 4.5, 5, 5.5, 6, 6.5, 7, 7.5, 8],
    warnings: [],
  },
  chords: [
    { startSec: 0, endSec: 2, chord: "Bb", confidence: 0.94, beatStart: 0, beatCount: 4, barStart: 0, isLowConfidence: false, rawChord: "Bb", correctedChord: null },
    { startSec: 2, endSec: 4, chord: "Eb", confidence: 0.91, beatStart: 4, beatCount: 4, barStart: 1, isLowConfidence: false, rawChord: "Eb", correctedChord: null },
    { startSec: 4, endSec: 6, chord: "F", confidence: 0.40, beatStart: 8, beatCount: 4, barStart: 2, isLowConfidence: true, rawChord: "F", correctedChord: null },
    { startSec: 6, endSec: 8, chord: "Gm", confidence: 0.90, beatStart: 12, beatCount: 4, barStart: 3, isLowConfidence: false, rawChord: "Gm", correctedChord: null },
  ],
};

test("upload to sheet, easy guitar, correction and follow position", async ({ page }) => {
  let jobPolls = 0;

  await page.route("**/analysis-api/analyze", async (route) => {
    await route.fulfill({
      status: 202,
      contentType: "application/json",
      body: JSON.stringify({ jobId: "job-1", status: "queued" }),
    });
  });

  await page.route("**/analysis-api/jobs/job-1", async (route) => {
    jobPolls += 1;
    if (jobPolls === 1) {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ jobId: "job-1", status: "running", result: null, error: null }),
      });
      return;
    }
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ jobId: "job-1", status: "complete", result: finishedResult, error: null }),
    });
  });

  await page.goto("/analyze");
  await expect(page.getByRole("heading", { name: "Analyse a song" })).toBeVisible();

  await page.getByTestId("audio-input").setInputFiles({
    name: "demo.wav",
    mimeType: "audio/wav",
    buffer: Buffer.from("RIFF-demo-audio"),
  });
  await page.getByRole("button", { name: "Analyse track" }).click();

  await expect(page.getByText(/Analysing/i)).toBeVisible();
  await expect(page.getByRole("heading", { name: "Demo" })).toBeVisible();
  await expect(page.getByText("Bb", { exact: true }).first()).toBeVisible();
  await expect(page.getByText("Needs review")).toBeVisible();

  await page.getByRole("button", { name: "Easy Guitar" }).click();
  await expect(page.getByText(/Capo 3/)).toBeVisible();
  await expect(page.getByTestId("chord-0")).toHaveText(/G/);
  await expect(page.getByTestId("chord-1")).toHaveText(/C/);
  await expect(page.getByTestId("chord-2")).toHaveText(/D/);
  await expect(page.getByTestId("chord-3")).toHaveText(/Em/);

  await page.getByRole("button", { name: "Original" }).click();
  await page.getByTestId("chord-0").click();
  await page.getByLabel("Correct chord 1").fill("Bb7");
  await page.getByRole("button", { name: "Save correction" }).click();
  await expect(page.getByTestId("chord-0")).toHaveText(/Bb7/);

  await page.getByRole("button", { name: "Easy Guitar" }).click();
  await expect(page.getByTestId("chord-0")).toHaveText(/G7/);

  await page.getByRole("button", { name: "Follow" }).click();
  await page.getByLabel("Playback position").fill("6.2");
  await expect(page.getByText("Now: Em")).toBeVisible();
});
