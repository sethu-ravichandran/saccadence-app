# Saccadence design system

Everything visual in this app comes from here. The source of truth is
`design-handoff/README.md` + `design-handoff/Saccadence Flow.dc.html`; this
package is that handoff expressed in Compose.

## The one rule

**Screen code never names a colour, a font, a size, or a radius.** It names a
role, and takes it from `ui/theme`. If you are about to write `Color(0xFF…)`,
`14.sp`, `FontFamily.Monospace`, or `RoundedCornerShape(11.dp)` in a screen,
stop: either the token exists and you should use it, or the role is genuinely
new and belongs in `ui/theme` first.

The two legitimate exceptions in the whole codebase are the camera
shutter-flash and dim overlays in `PairingScreen` — pure white and pure black,
because they are photographic effects on a camera frame rather than surfaces.

## Layout

```
ui/theme/
  Color.kt    SaccadenceColors — every colour, named by role
  Type.kt     Manrope + Space Mono families, SaccadenceType scale
  Dimens.kt   Spacing, Radius, Sizes, ChipMetrics
  Theme.kt    SaccadenceTheme — wraps the app; maps tokens onto MaterialTheme
ui/components/
  Buttons.kt      PrimaryButton, SecondaryButton, EmphasizedOutlineButton,
                  DestructiveOutlineButton, TextAffordance, SegmentedSelector,
                  PillChip, Tag
  Fields.kt       LabeledTextField, NotesField, FieldLabel
  Layout.kt       ScreenScaffold, ScreenTitle, MonoEyebrow, Hairline,
                  DashedRule, InfoRow, CheckRow, SquareCheckbox, HomeIndicator
  Indicators.kt   StatusBlock + StatusTone, StatusDot, StatCard, StepBar,
                  InfoNote, FooterCaption, SegmentedPhaseBar
  DarkSurfaces.kt DarkPane, DarkInnerSurface, VideoChip, RecordingChip,
                  PreviewChip, CaptureQualityLine + CaptureQuality,
                  CapturePrompt, StimulusInstruction, MirrorCaption,
                  PhaseHeader, DarkWarningStrip
```

## Things that are load-bearing, not decoration

**Mono means measured.** Manrope for prose, Space Mono for anything the
instrument produced — latencies, gains, counts, IP addresses, session codes,
timestamps, frame rates, eyebrow labels. A latency set in Manrope reads as
someone's opinion; the same number in Space Mono reads as a reading. This split
is the single most visible thing about the design, so keep it exact.

**Colours are exact oklch conversions, not the handoff's hex table.** The
handoff authors every colour in `oklch()` and offers hex only "for tools that
need them"; several of those approximations drift visibly (its accent
`#6C5BC4` vs. the true `#6B66B4`). Since the design board is an HTML file that
hands raw `oklch()` to the browser, the browser's conversion is what the
designer sees — so `Color.kt` carries that, computed with the standard OKLab
matrices. If you add a colour, convert it the same way rather than eyeballing.

**A pulsing dot means "still working"; a static dot means "settled".** That is
why `StatusTone.Progress` pulses and `Good`/`Warning` do not. Don't animate a
settled state, and don't leave a working one still.

**The dark surfaces are not a dark mode.** The rig mirror and the live capture
are dark because a stimulus mirror and a camera feed have to be. They use the
same accent and neutral ramps as the light screens, they stay dark regardless
of the system setting, and there is deliberately no dark `ColorScheme`. A
clinician with the system in dark mode still gets white intake forms, because a
screening reading has to look the same in every room.

**The step bar's total lives in one place.** `StepProgress`'s `total`
parameter, defaulting to 7. The handoff's board shows 4 because its flow is
shorter than this app's — the app's flow is authoritative.

## Working against Material

`SaccadenceTheme` maps the tokens onto a Material `ColorScheme` and
`Typography`. That is a **safety net, not an interface**: it exists so the few
genuinely-Material components left (`AlertDialog`, `TextButton`, the press
ripple) land on-palette, and so any stray `MaterialTheme.typography.bodyMedium`
degrades to correct Manrope rather than to Roboto. New UI should not be written
against `MaterialTheme.*`.

Prefer the components here over Material equivalents. They exist because the
Material versions have the wrong anatomy, not merely the wrong colours —
`OutlinedTextField`'s label is a notch inside the border rather than a label
above the box, and `Checkbox` brings a 48 px touch box that breaks the
checklist's row rhythm.

## Animation

Every animation that existed before the re-skin is preserved, at its original
timing: the splash logo's scale/fade, the QR reticle's sweep and pulse, the
scan-capture flash → laser → zoom → dim → text sequence, and the intake
`AnimatedContent` slide. Only their colours moved onto the palette.

The handoff's own animation spec, for new work:

| What | Spec |
| --- | --- |
| Status and recording dots | opacity 1 → 0.35 → 1, 1.1–1.2 s, ease-in-out, infinite |
| Focused-field caret | same pulse at 1.1 s (`BasicTextField`'s own cursor) |
| Operator-driven screen change | horizontal slide, 200–250 ms |
| Automatic stage change | cross-fade |

That last distinction is deliberate: the patient should be able to tell "the
app moved on by itself" from "I moved it".

## What the handoff specifies that this app does not build

The handoff describes a 12-screen flow that differs from the app's. The re-skin
adapted the design onto the real flow rather than reshaping the flow, so these
handoff elements still have no counterpart here. Tokens and components for them
are present and ready:

- **Stage ribbon** (INTAKE · PAIRING · CALIB · TEST · RESULT) — the app has no
  standalone Calibration or Test screens to ribbon across.
- **"PHASE n OF 4" header and segmented bar** — `PhaseHeader` and
  `SegmentedPhaseBar` are built; the app has eight `TrialPhase` values, not
  four, so the mapping is a product decision rather than a styling one.
- **Degraded-capture prompt** ("Open your eyes wide") and the blink counter —
  `CapturePrompt` is built, but nothing in the pipeline currently reports a
  blink to the UI in real time.
- **Screen H1s where the app has none** — e.g. the symptoms step opens straight
  into the step bar. The handoff's headings there are new copy, not a re-skin.

Unused tokens and components are intentional: they are the design's vocabulary,
and having the role already named is what stops the next feature from inventing
a ninth grey.

## Three handoff features that *were* added

These went beyond repainting, so they are called out rather than buried:

**Stimulus target dot and rail.** `RigStimulusMirror` now draws the rig's
target in all three trial blocks — a steady centre dot for fixation, a pulsing
dot with a dim previous-position dot for the saccade block, and a dot gliding
along a rail for pursuit, each captioned with the rig parameter in play. The
fixation dot deliberately does not animate: the patient is holding their gaze,
so anything moving there would be a competing stimulus. The pursuit animation's duration is derived from the rig's
own commanded velocity and amplitude, so the mirrored dot keeps pace with the
real one.

It is fed by a new `onStimulus` callback on `TrialSessionController`, carrying
`StimulusMirror`. **This path is presentation-only** — the measurement pipeline
buffers the same rig events independently, so a bug in the mirror can make the
picture wrong but can never move a latency.

One caveat: `RigEvent.TargetStep.x` is the only field in the wire protocol
whose units this repository does not pin down — nothing else reads `x` or `y`.
It is assumed to be a 0..1 fraction of the rig's screen width, isolated in
`StimulusMirror.kt`'s `xFraction()` so that it is the single thing to change if
the rig actually sends pixels or degrees.

**Calibration stat cards.** Three `StatCard`s over the live feed during every
marker phase, from a new `PreviewStatsTracker`. Two of the handoff's three
stats map directly; the third does not, and the substitution is deliberate:

| Handoff | Here | Why |
| --- | --- | --- |
| FPS | FPS | Real analyzed frame rate, rolling 2 s window. |
| JITTER (ms) | JITTER (px) | The guard square's positional spread. Clock jitter in ms does not exist until a calibration window closes and `ClockCalibrator` has two edges to disagree about — showing px as ms would be inventing a number. |
| DROPPED % | NO DECODE % | Share of frames yielding no marker decode. Same polarity, and it is the failure the operator can fix by re-aiming. |

A dash means "not measured yet", not zero — a rate needs more than one frame
and guard jitter needs a full window. The JITTER card colours itself against
`LOCK_JITTER_THRESHOLD_PX`, the same threshold that decides lock, so a
warning-coloured number and a refusal to lock always agree.

**RECENT doctor chips and Erase all records.** Chips are sourced from the
doctor names already on the device, read once per screen entry so tapping one
can't reshuffle the row under the finger tapping it. `Erase all records` sits in
the patient list's footer with the handoff's caption, deletes the store file
outright rather than writing an empty array, and confirms first.
