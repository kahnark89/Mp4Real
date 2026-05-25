# shadow-mode-labeler

Calibration sprint tooling for Phase 1 of ArcShield. Implements CLAUDE.md §4.3.

## Where the code lives

The Android Compose module is at `android/shadow-mode-labeler/` — it is part of the
Gradle project rooted at `android/`.

## Purpose

During the two-week shadow-mode calibration window:

1. The LLR gate (`core-llr/llrGate()`) runs in shadow mode — emitting a `CandidateWindow`
   every 500 ms without persisting any containers.
2. `CandidateWindowLog` (in this module) appends each `CandidateWindow` to an NDJSON file
   at `filesDir/shadow_mode/<date>.ndjson`.
3. At end-of-shift the operator opens the labeler screen. Non-max suppression (60 s window)
   collapses the 57 600 ticks into ~20–480 candidate events.
4. The operator marks each candidate TP (real expert decision event) or FP (noise).
5. `TauCalibrator` recommends τ targeting ≥ 80% TP / ≤ 20% FP.
6. The operator exports the labeled set to `<date>.labels.json` for offline analysis.

## Wiring into the app

The labeler module is an Android library. To use it:

```kotlin
// In :app build.gradle.kts (debug only):
debugImplementation(project(":shadow-mode-labeler"))

// In your debug Activity / NavHost:
import com.capsconc.arcshield.labeler.ui.LabelerScreen
// ...
LabelerScreen()
```

The `:app` module is currently empty (Phase 1). Wire up when the app module is scaffolded.
