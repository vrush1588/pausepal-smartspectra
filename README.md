# Pause Pal SmartSpectra

A consent-first study-break buddy for students, developers and learners. Built for the
**Presage SmartSpectra SDK Challenge**.

Pause Pal uses the front camera and the **Presage SmartSpectra SDK** to read *your own* live pulse
and breathing on the phone. During the first part of a session it learns **your personal breathing
baseline**. If your breathing then stays clearly above that baseline, a friendly mascot suggests a
short break with a break tip and an animated breathing circle. It is a wellness helper only:
no diagnosis, no medical advice.

- DEV post: _TODO: add link_
- Demo video: _TODO: add link_

## Session flow (5 screens, one Activity)

1. **Consent**: how the camera is used, a required consent checkbox, and two optional switches
   (facial expressions, SDK diagnostics), both OFF by default.
2. **Choose session**: "Choose your check-in length": 3 min (Quick check-in, default), 5 min
   (Short reset) or 10 min (Longer), then "Start session".
3. **Live session**: countdown, "Camera active" badge, SDK status and validation hints, pulse,
   breathing, HRV (and expression if you opted in), live chest and belly waves, and an optional
   small camera preview. "Pause camera" stops the SDK and shows "Camera off" while the timer keeps
   counting; "Resume camera" restarts it. The mascot breathes along with your belly wave. An
   optional **"Gentle vibration"** switch (off by default) turns on soft haptics.
4. **Break nudge** (bottom sheet): break tips, guided breathing circle, "Take a break" (60 s guided
   breathing) or "Not now" (snooze). During the break: a breathing circle that follows your own
   breathing, a fixed-rhythm guide ring, a small tap-to-tick checklist, and a neutral
   "before X /min, after Y /min" when the 60 s end.
5. **Summary**: session length, breaks taken, average pulse and breathing, last HRV, and your
   baseline breathing. "Start new session". Nothing is saved: the numbers are erased as soon as
   you leave this screen.

**Light and dark themes:** an **Auto / Light / Dark** switch sits at the top of the Consent and
Choose-session screens. Auto follows the phone's setting. The choice is held in memory only, like
everything else, so it goes back to Auto when the app is closed.

## Live Presage signals used (where to verify)

All SmartSpectra SDK calls are in **one file**:
[`app/src/main/java/com/app/pausapal/presage/PresageSession.kt`](app/src/main/java/com/app/pausapal/presage/PresageSession.kt).

| Signal | SDK field | Where it is read | Used for |
|---|---|---|---|
| Pulse rate (bpm) | `metrics.cardio.pulseRateList` | `PresageSession.onMetrics()` | Pulse card, summary average |
| HRV RMSSD (ms) | `metrics.cardio.hrvList[].rmssd` | `PresageSession.onMetrics()` | HRV card, summary "last HRV" |
| Breathing rate (/min) | `metrics.breathing.rateList` | `PresageSession.onMetrics()` | Breathing card, **personal baseline + break trigger** |
| Chest motion trace | `metrics.breathing.upperTraceList` | `PresageSession.onMetrics()` | "Chest wave" live chart |
| Belly motion trace | `metrics.breathing.lowerTraceList` | `PresageSession.onMetrics()` | "Belly wave" live chart |
| Facial expression (opt-in) | `metrics.face.expressionList` | `PresageSession.onMetrics()` | Expression card (only if opted in) |
| Processing status | `sdk.processingStatus` | `PresageSession.bind()` | Starting / Measuring / Error chip |
| Validation status | `sdk.validationStatus` | `PresageSession.bind()` | Positioning hints (e.g. "Keep your upper chest in view") |
| Errors | `sdk.error` | `PresageSession.bind()` | Error notice |

SDK setup and control are in `PresageSession.start()` and `PresageSession.stop()`:
`SmartSpectraSdk.shared`, `config.apiKey`, `config.imageOutputEnabled = false`,
`config.enableTelemetry`, `config.previewSurfaceProvider`,
`config.requestedMetrics = breathingMetrics + cardioMetrics (+ EXPRESSIONS if opted in)`,
`useCamera(CameraSelection.Front)`, `start()` and `stop()`.

Rate lists can be empty between updates. A reading is used only when its timestamp is newer than
the last one, so the UI keeps the last valid value and never overwrites it with null. Trace samples
are buffered locally (last 300) before drawing.

**Break logic** is in [`session/BreakCoach.kt`](app/src/main/java/com/app/pausapal/session/BreakCoach.kt),
with unit tests in `app/src/test/.../BreakCoachTest.kt`:

- Baseline = average of breathing readings from 30 s to 60 s into the session for 3 min sessions,
  or 30 s to 90 s for 5 and 10 min sessions. If fewer than 3 readings arrived by then, the window
  extends until it has 3.
- If the camera is paused for more than 60 s, the baseline is discarded on resume and learned again
  (same window, measured from the moment the camera resumed). Shorter pauses keep it.
- After that, a break is suggested when the rate is **> baseline × 1.25 and ≥ baseline + 3 /min for
  2 consecutive new readings**, and you are not inside a snooze or cooldown.
- "Not now" snoozes for 2 min in 3 min sessions, 5 min in 5 and 10 min sessions. "Take a break" counts the break, starts 60 s of
  guided breathing, and applies the same cooldown.

## Setup

### Toolchain (as built and tested to compile)
- Android Studio with **AGP 9.2.1**, **Gradle 9.4.1**, **Kotlin 2.2.10** (Compose compiler plugin)
- **compileSdk 37** (SmartSpectra needs 36.1+; the androidx core 1.19 and lifecycle 2.11 libraries
  in this project need 37). targetSdk 36, **minSdk 28**
- JDK 17+ (the Android Studio bundled JBR works)
- Dependencies: `com.presagetech:smartspectra:3.4.0`, `androidx.camera:camera-view:1.6.0`, Compose
  (BOM 2026.02.01). Repositories: `google()`, `mavenCentral()`

### API key
1. Get an API key from Presage (https://physiology.presagetech.com).
2. Add it to **`local.properties`** in the project root (this file is git-ignored):
   ```properties
   PRESAGE_API_KEY=your_key_here
   ```
3. Rebuild. The key is injected into `BuildConfig.PRESAGE_API_KEY` by `app/build.gradle.kts`.
   It is never hard-coded or committed. Without a key, the live screen shows an error and the
   camera does not start.

### Run
- Use a **physical Android phone** (Android 9 / API 28 or newer, arm64). The emulator produces no
  usable signal.
- `./gradlew :app:installDebug`, or Run from Android Studio.
- Unit tests: `./gradlew :app:testDebugUnitTest`

## Tips for good readings
- Keep the phone **steady**: prop it up rather than holding it.
- Use **even, soft light** on your face. Avoid strong light from behind you.
- Keep your **face and upper chest in view** of the front camera.
- **Don't talk** while breathing readings settle. Breathing is first shown at about 10 s and is
  reliable at about 30 s. Pulse is a 12 s average, and HRV needs about 60 s.
- Short check-ins work best. In a **3 min** session the baseline is ready at 60 s and the snooze
  is 2 min.

## Consent and Data Handling

- **Camera consent and stop control.** The camera never starts until you tick
  "I agree to use my camera for this session" (Continue is disabled until you do), tap
  **Start session**, and grant Android's camera permission. A red-dot "Camera active" badge is shown
  for the whole session. "Stop and see summary" stops the camera at any time. Leaving the app
  (`onPause`) stops the SDK and the camera immediately, so it **never runs in the background**. You
  can revoke camera permission anytime in Android settings.
- **On-device frame processing.** Video frames are analysed on the phone by the SmartSpectra SDK.
  The app sets `imageOutputEnabled = false`, so it never even receives camera images.
- **No recording.** No video or photo is recorded or stored. The optional preview is a live view only.
- **Memory-only storage.** Readings live only in memory (a ViewModel). There are no files,
  SharedPreferences, databases or logs of vitals, and no pulse or breathing values are written to
  Logcat. `android:allowBackup="false"` and the data-extraction rules exclude everything from cloud
  backup and device transfer.
- **No sharing, accounts or ads.** No sign-in, no analytics SDK, no ads, and no sharing of readings.
- **Nothing is saved after a session.** The summary is shown once, and its numbers are erased as
  soon as you leave it ("Start new session", Back, or switching away from the app). Closing the app
  also discards everything, including your consent choices. There is no history to delete.
- **The SDK contacts Presage's servers.** The SmartSpectra SDK needs internet to **check the API key
  and download its models**. Video frames are not uploaded by this app. (The SDK's own manifest adds
  `INTERNET` and `ACCESS_NETWORK_STATE`.)
- **SDK aggregate telemetry: off by default, opt-in.** The SDK can send optional aggregate
  diagnostics (performance data only: no video, no vitals, no identifiers). In SDK 3.4.0
  `SmartSpectraConfig.enableTelemetry` defaults to `true`, so Pause Pal **forces it to `false` at app
  launch** (`PresageSession.applyPrivacyDefaults()`, called from `MainActivity.onCreate`) and only
  sets it to `true` for a session if you turn on "Allow anonymous SDK diagnostics".
- **No AI or LLM service.** The app uses no AI or LLM service.
- **Optional haptics, off by default.** The "Gentle vibration" switch on the live screen is off by
  default. When on, the phone gives one soft vibration when a break is suggested and a very light
  tick on each guided inhale. This uses the `VIBRATE` permission (a normal permission with no
  prompt and no data access). The app never vibrates unless the switch is on.
- **Optional voice guidance, off by default.** The "Voice guidance" switch on the live screen reads
  fixed prompts aloud using Android's built-in text-to-speech: "Breathe in", "Breathe out slowly",
  the chosen break tip, and short camera-framing tips such as "Move a little closer." It is off by
  default, receives no readings (no pulse, breathing or HRV value is ever spoken), and prefers
  offline voices: the text-to-speech engine is only created when you turn the switch on, and only a
  voice that needs no network connection is used. If the phone has no offline voice, the switch
  turns itself off and says so. Speech stops when the break sheet closes, the session ends or you
  leave the app. With voice off, the app plays no sounds.
- **Interactive touches use the same in-memory data.** The breathing mascot, the breathing circle,
  the before/after rates and the break checklist all reuse the readings already on screen. They add
  no new data, storage, network calls or AI.
- **Optional expressions, off by default.** `MetricType.EXPRESSIONS` is requested only if you turn on
  "Analyse facial expressions (optional)". Otherwise the expression card is hidden and no expression
  metrics are requested.
- **No diagnosis, no medical decision-making, no covert monitoring.** Pause Pal is a wellness tool,
  not a medical device. The baseline is *yours*, not a medical norm. The app never uses diagnostic
  or emotional-state labels, and it only measures while you can see that the camera is active.

## Project layout

```
app/src/main/java/com/app/pausapal/
├── MainActivity.kt              navigation, camera permission, onPause stop, session ticker
├── presage/PresageSession.kt    ALL SmartSpectra SDK calls + metrics parsing
├── session/
│   ├── BreakCoach.kt            personal baseline + break-suggestion rules
│   ├── SessionModels.kt         break tips, lengths, sheet state, summary
│   └── SessionViewModel.kt      in-memory state
└── ui/
    ├── components/              Mascot (Canvas), BreathPhase, WaveChart, cards/buttons/glyphs
    ├── haptics/Haptics.kt       optional gentle vibration (checks the switch on every call)
    ├── screens/                 Consent, ChooseSession, LiveSession, BreakSheetOverlay, Summary
    └── theme/                   "Nocturne Serenity" tokens from the Stitch DESIGN.md
```
## How it works

Pause Pal uses the phone's front camera and the Presage SmartSpectra SDK to estimate
a few body signals while you study, code or read. All numbers are for your own
wellness awareness. They are not medical measurements.

### What the camera reads

Each heartbeat causes tiny colour changes in facial skin, and each breath causes
small movements of the chest and shoulders. A camera can pick up both, with no
wearable or contact. The SDK analyses the video frames on the phone and turns them
into the readings below.

### The readings

| Reading | What it means | When it appears |
| --- | --- | --- |
| **Pulse (bpm)** | Heartbeats per minute, averaged over about 12 seconds | after ~12 s |
| **Breathing (/min)** | Breaths per minute (one inhale plus one exhale) | first value ~10 s, settles ~30 s |
| **HRV (ms, RMSSD)** | How much the time between heartbeats varies, in milliseconds | after ~60 s |
| **Chest wave** | Live line of upper-chest movement: up on inhale, down on exhale | immediately |
| **Belly wave** | Live line of abdomen movement: up on inhale, down on exhale | immediately |
| **Expression** (optional, off by default) | The SDK's facial expression label | only if you turn it on |

Slow breathing draws long, smooth waves. Fast breathing draws short, tight ones.

### How the break suggestion works

1. The first ~30 seconds are a warm-up while the signal settles.
2. The app learns **your own** breathing baseline (the average over the next 30 to 60 seconds).
3. If your breathing stays well above **your** baseline (at least 25% and 3 breaths per minute higher, on two readings in a row), a gentle sheet suggests a short break.
4. The sheet shows break tips and a guided breathing circle.
5. "Not now" snoozes the suggestion. "Take a break" starts a one-minute guided breathing reset.

The app never compares you with a "normal" range, and it never says you are stressed,
anxious or unwell. It only notices a change from your own baseline.

### Interactive touches

- **The mascot breathes with you.** Its body and glow grow and shrink with your live belly wave
  (smoothed so it does not jitter). Its face is its own mood, not a label for you: neutral before
  your baseline is set, calm near your baseline, lively above it.
- **A breathing circle that follows you.** In the break sheet, the filled circle follows your live
  chest or belly wave, and a faint ring keeps a steady rhythm of 4.5 s in and 4.5 s out as a target.
  The "Breathe in…" / "Breathe out slowly…" prompt follows the ring. With no wave signal, the circle
  falls back to the steady rhythm.
- **Before and after.** When you tap "Take a break", the app notes your latest breathing rate. When
  the 60 s end, it shows "before X /min, after Y /min" with no good or bad labels. The breathing
  rate is a 30-second average, so "after" changes gradually. If a reading is missing, it says "not
  enough data yet".
- **Break checklist.** Two or three tap-to-tick steps (for example "Sip water").
  When all are ticked, the mascot waves: "Nice pause!".
- **Gentle vibration (optional, off by default).** One soft buzz when a break is suggested, and a
  light tick on each guided inhale.

There are no scores, streaks, or stress or health labels.

### Tips for good readings

- Prop the phone on a stand, since a handheld phone shakes too much for breathing.
- Use even, steady light and avoid screens or lamps that flicker.
- Keep your face and upper chest in view.
- Sit still and avoid talking while breathing is measured.
- Keep sessions short (3 to 10 minutes). Readings need a steady setup.

### What this is not

Pause Pal is a wellness and study-habit tool. It does not diagnose, treat or
monitor any condition, and it gives no medical advice or decisions.