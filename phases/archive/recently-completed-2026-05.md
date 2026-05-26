# Recently Completed — archived from CURRENT_PHASE.md §5

Older §5 entries moved here to keep the live file at 10 entries. History only; do not edit.

| Date | ID | Item | Notes |
|---|---|---|---|
| 2026-05-25 | W-005 | `source-camerax` module — `CameraXCaptureSource` implementing `CaptureSource` (CameraX ImageAnalysis NV21 + AudioRecord 48kHz mono); `FrameDiffMotion` (Y-plane MAD with subsample=4); `Λ_motion` Gaussian-shift GLR wired in `LlrGate`; motion baseline fields added to `LlrBaseline`; `buildBaseline()` accepts `videoFrames` flow; CameraX 1.3.4 added to version catalog; 8 `FrameDiffMotionTest` unit tests; unsigned byte handling verified | Λ_motion now live when `motionAvailable = true` in baseline; 0.0 fallback when no video source connected |
| 2026-05-25 | W-004 | `LlrBaselineBuilder` — `buildBaseline()` suspend function + `SpectralAccumulator` (Welford per-bin FFT mean/variance) + `computeRmssdStats()` (20-beat sub-window RMSSD variance) + Welford online accel-RMS stats; injectable clock for JVM testability; 13 unit tests with `runTest` + finite flows | Timeout-based collection works for both production (infinite sensor flows cancel at durationMs) and tests (finite flows complete naturally) |
| 2026-05-25 | W-003 | `core-llr` Λ_bio — `RollingBioStats` (rolling HR mean/variance + RMSSD), activity gate (resting/light/moderate/vigorous), HR-delta GLR + RMSSD-deviation GLR wired into `llrGate()`; 10 unit tests; `activityGate` field added to `CandidateWindow`; bio fields added to `LlrBaseline`; `hrSamples`/`rrSamples` optional params on `llrGate()` | Nonlinear HRV (SD1/SD2, sample entropy) stubbed as `lambdaHrvNl = 0f` — Phase 2 after H10 ECG path live |
| 2026-05-24 | W-002 | `core-llr` Λ_env gate — `RealFft` (Cooley-Tukey), `KlDivergence`, `RollingAccelRms` (Welford), `LlrBaseline`, `LlrConfig`, `CandidateWindow`, `llrGate()` in shadow mode; 3 unit test classes | Λ_motion + Λ_gaze = 0.0 stubs; Λ_bio = 0.0 placeholder wired for next item |
