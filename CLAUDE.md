# Saccadence — Android app

Phone-based oculomotor screening aid for vertigo. A paired external rig
(`saccadence-rig`, a clinic laptop) displays a moving target and timestamps
every frame; this phone films the patient's eyes; the two timestamped streams
are correlated into a saccade-latency and pursuit-gain readout with an explicit
error budget.

Kotlin + Compose, `minSdk` 26. Build with `gradle :app:assembleDebug`; unit
tests are plain JVM (no Robolectric) via `gradle :app:testDebugUnitTest`.

## UI work: read `app/src/main/java/com/arra/saccadence/ui/README.md` first

The app has a design system under `ui/theme` and `ui/components`, transcribed
from `design-handoff/`. When touching anything visual:

- **Take colours, type, spacing, radii and sizes from `ui/theme` — never write
  a literal.** No `Color(0xFF…)`, no bare `.sp`, no ad-hoc
  `RoundedCornerShape`. If the role doesn't exist yet, add it to `ui/theme`
  first, then use it.
- **Build from `ui/components`, not from Material.** Those components exist
  because the Material equivalents have the wrong anatomy, not just the wrong
  colours. `SaccadenceTheme`'s Material mapping is a safety net for
  `AlertDialog`/`TextButton`/ripple — don't write new UI against
  `MaterialTheme.*`.
- **Mono means measured.** Space Mono for anything the instrument produced
  (latencies, gains, counts, IP addresses, session codes, frame rates, eyebrow
  labels); Manrope for prose. This split is load-bearing.
- **Don't convert the dark surfaces into a dark mode.** The rig mirror and live
  capture are dark because of what they show. There is no dark `ColorScheme`
  and should not be one.
- **Preserve existing animations and their timings.** Re-skins change colours,
  not durations or easing.

New colours must be converted from the handoff's `oklch()` values with the
standard OKLab matrices, not copied from its hex table — that table's
approximations drift visibly from what the design board actually renders. The
`ui/README.md` explains why.

## Domain constraints that the code depends on

- **Raw frames are never persisted.** Frames are analysed and discarded; only
  measured results and patient details are stored, on-device, never uploaded.
  The consent screen promises exactly this, so any change here is a change to
  a legal notice, not just to storage.
- **`ConsentScreen`'s copy is the app's DPDPA-2023 compliance answer**, and
  `SymptomFlag`'s questions are clinically chosen. Both are approved copy —
  don't reword either to match a design mock or to tighten prose. (The design
  handoff's own consent and symptom copy is explicitly placeholder.)
- **The rig-stimulus mirror is presentation-only.** `TrialSessionController`'s
  `onStimulus` callback and `StimulusMirror` exist so the operator can see the
  target; the measurement path buffers the same rig events independently. Never
  let the mirror become the source for anything measured.
- **`RigEvent.TargetStep.x`/`y` units are unverified** — nothing but the mirror
  reads them, and the rig repo is not in this tree. The 0..1-fraction
  assumption is isolated in `StimulusMirror.kt`'s `xFraction()`.
- **The camera session is deliberately continuous** from `SetupCalibration`
  through `PostCalibration`, and the analyzer reads the live phase through a
  `rememberUpdatedState` holder rather than closing over the parameter. Both
  facts are load-bearing; see the doc comments on `EyeCaptureArea` and
  `GuardTracker`.
- **`PreviewView` uses `ImplementationMode.COMPATIBLE`** (TextureView-backed)
  in both camera surfaces — the SurfaceView default renders black and bleeds
  over neighbouring Compose content on this OEM skin.

## Flow

`Splash → Pairing (QR-first, manual fallback) → TrialStepper → Results`.

Intake lives *inside* the stepper (details → doctor → symptoms → consent,
steps 1–4 of 7) while the rig connection settles, with the rig's live state
mirrored in the top half throughout; steps 5–7 are phase-driven, not
user-navigated. The `design-handoff/` board describes a different, longer flow
(consent-first, standalone calibration and test screens, 4 intake steps) — the
app's flow is authoritative, and the design was adapted onto it.
