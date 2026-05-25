# CURRENT_PHASE.md

**This file is the live state of the ArcShield build. It changes often. CLAUDE.md does not.**

The handoff document (`/CLAUDE.md`) describes architecture, schema, and invariants that should never drift. This file describes where the build currently is, what's actively in flight, what's blocking, and what's next. Update it at the end of every working session.

> **Re-entry rule:** Read CLAUDE.md first for architecture. Read this file second for state. Never invert that order.

---

## 0. At a Glance

| | |
|---|---|
| **Current Phase** | _Phase 1 — Container + LLR gate, shadow mode_ |
| **Phase start date** | 2026-05-24 |
| **Target completion** | ~2026-07-05 (6 weeks) |
| **Calendar week of phase** | Week 1 of 6 |
| **Validation site status** | PPVC Line 1 — awaiting facility agreement |
| **Corpus depth** | 1 validated CIAER+ event (April 8, 2026 PIE demo — material_segregation_funnel_flow) |
| **Codebook size** | _0 primitives_ |
| **Last shift captured** | _YYYY-MM-DD / none yet_ |
| **Last working session** | 2026-05-25 — Claude Code — W-010: llm-claude — ClaudeVisionClient + parseGaugeValue + MockWebServer tests; okhttp added to version catalog |
| **Build is** | 🟢 healthy |

---

## 1. Active Work Items

Things being worked on right now. Move items here from §3 (Backlog) when starting; move to §5 (Completed) on acceptance. Limit WIP to 3 active items at a time — more than that means something is actually blocked and should be in §4.

| ID | Item | Module(s) | Started | Owner | State |
|---|---|---|---|---|---|
| | | | | | |

### Per-item working notes

_(No active items. Pull from §3 when starting.)_

---

## 2. Phase 1 Acceptance Tracker

From CLAUDE.md §11. Do not advance phases until every criterion is checked.

- [x] `core-capture`, `core-codec`, `core-llr` modules functional  ← core-llr ✅ core-codec ✅ core-capture ✅
- [ ] `source-polar` (H10) consuming PMD streams
- [x] `source-camerax` POV video
- [ ] Phone-side mux pipeline writing fMP4 with all five Phase 1 tracks (POV, acoustic, vibration, biometric, voice)
- [ ] LLR gate operates in shadow mode (logs candidates, never persists a container)
- [ ] ε_sync measured and logged per session  ← EpsSyncMeasure + sidecar writer ✅; EpsSyncCoordinator ✅; end-to-end requires device test
- [ ] ε_sync sustained ≤ 100 ms across at least 5 production shifts
- [ ] 20–30 candidate windows logged
- [x] Hand-labeling tool (`tools/shadow-mode-labeler`) operational
- [ ] All BLE dropouts emit explicit `BiometricGap` records (no silent interpolation)

**Phase 1 hard stop:** if ε_sync exceeds 250 ms persistently, halt and debug Polar↔phone clock anchor before proceeding. Document the failure mode here:

_None observed yet._

---

## 3. Backlog (Next Up)

Ordered by intended pickup, not by priority alone. Top of list is next.

1. **Bench-test `PolarBleBiometricSource` against H10** over a 4-hour continuous capture; characterize dropout rate near the extruder barrel. Hardware-gated; requires signed facility agreement.
2. **Scaffold `:app` module** — Compose entry point + Hilt DI wiring all sources → core-capture → core-codec. Wire `CaptureSession.candidateWindows` flow into `LabelerScreen` as debug overlay. Shadow-mode-labeler needs to be reachable from the main screen.
3. ~~**Wire `core-llr` Λ_bio** from HR delta + HRV-RMSSD ratio — Polar PMD-derived.~~ **DONE 2026-05-25** — see §5.
4. **Implement nonlinear HRV (SD1/SD2 + sample entropy) in `core-llr`** — Phase 2 once H10 ECG is live. Currently stubbed as `lambdaHrvNl = 0f` in `LlrGate.kt`.
5. ~~**Scaffold `source-camerax` module** and wire `Λ_motion` (frame-to-frame video energy) in `LlrGate.kt`.~~ **DONE 2026-05-25** — see §5.
6. ~~**Build `tools/shadow-mode-labeler`** as a minimal Compose screen reading candidate windows from local storage.~~ **DONE 2026-05-25** — see §5.
7. **[MCP-MOD-001] `SqliteCorpusBackend`** — Trigger: `corpus_depth > ~500` OR `list_failure_modes` latency > 200 ms. New file `backend/api/arcshield/corpus/backends/sqlite_backend.py`. Use `aiosqlite`; indexed columns: `failure_mode_tag`, `escalation_state`, `graph_weight`, `operator_id`. Add `"sqlite"` to the `params` fixture in `tests/test_corpus_backend_contract.py` — all 28 contract tests run automatically. Factory entry: `get_backend("sqlite", ...)` in `backends/__init__.py`. Config switch: `config.toml [backend] type = "sqlite"`.
8. **[MCP-MOD-003] `query_by_cause_signature` similarity upgrade** — Phase 2: after `escalation_state` index pre-filter, compute per-instrument normalized distance `1 / (1 + |query_val − stored_val|)` instead of coverage-only score. Update the `IMPLEMENTATION NOTE` marker in `server.py` and the abstract method docstring in `backend.py`. Phase 3: replace with embedding ANN (HNSW) once corpus exceeds ~200 events per `failure_mode_tag`.
9. **[MCP-MOD-004] Per-operator write auth** — Phase 2. Add `[auth] write_operators = [...]` to `config.toml`. In `server.py` `ingest_event` and `update_graph_weight`, verify `event.operator_id` / `updated_by` against the allowlist; reject with `_error(..., "AUTH_FAILED", ...)`. Token passed as tool argument; upgrade to MCP header when header support lands.
10. **[MCP-MOD-007] `get_divergent_chains` stub** — Add as 8th MCP tool in `server.py` returning `NOT_AVAILABLE` when `GraphCorpusBackend` is absent. `CorpusBackend.get_divergent_chains()` already has a `NotImplementedError` default. Exposes the divergence-point surface (whitepaper §3.4) to agents early.

When pulling an item from this list into §1, copy its text verbatim and assign a W-### ID.

---

## 4. Blocked / Flagged

Items that cannot advance until something external resolves. Each entry needs an unblock condition.

| Item | Blocked on | Unblock condition | First flagged | Last poked |
|---|---|---|---|---|
| Non-provisional patent filing | Patent counsel + budget | File before provisional expires (prior art priority date: 2026-04-08 — 12-month window closes ~2027-04-08). arXiv submission precedes non-provisional per Q6.1 rule. | 2026-05-24 | 2026-05-24 |
| Facility deployment agreement with Hollowell | Legal sign-off | Signed MSA + data-rights addendum | 2026-05-24 | 2026-05-24 |
| ~~PLC API integration (Phase 3 prep)~~ | ~~Vendor access + IT scope clarification~~ | **RESOLVED 2026-05-25** — Bypassed via architectural pivot to `source-vision-telemetry` for optical R_phys extraction. | 2026-05-24 | 2026-05-25 |
| ~~[MOD-009] Corpus bootstrap~~ | ~~Facility agreement + active capture~~ | **DONE** — seed event 6ab2942f ingested 2026-05-24 | 2026-05-24 | 2026-05-24 |

---

## 5. Recently Completed

Last 10 items max. Anything older lives in version control.

| Date | ID | Item | Notes |
|---|---|---|---|
| 2026-05-25 | W-010 | `llm-claude` module — `ClaudeVisionClient` implementing `LlmClient`; `VideoFrameEncoder` (NV21→JPEG→Base64); `parseGaugeValue()` regex extraction handles bare numbers, unit suffixes, tilde prefixes, negatives; `buildRequestJson()` constructs image+text content blocks; OkHttp 4.12 + MockWebServer tests; 12 unit tests | API key injected at DI layer — never committed; Haiku default for cost/latency; generateGuidance() is Phase 3 stub |
| 2026-05-25 | W-009 | `core-capture` module — `ChannelRingBuffer<T>` (generic ring buffer, thread-safe via RWLock, capacity eviction); `WindowExtractor` (extracts 5-track window from rings); `EpsSyncCoordinator` (NTP-style sync schedule, injectable clock); `VideoEncoderDelegate` + `AudioEncoderDelegate` interfaces; `MediaCodecVideoEncoder` + `MediaCodecAudioEncoder` (async callback, CSD via Deferred<ByteArray>); `CaptureSession` (full I-frame → baseline build → muxer init → live gate orchestration); 20 JVM unit tests (8 ring buffer, 5 window extractor, 7 eps sync) | CaptureSession.start() suspends for iFrameDurationMs, builds LlrBaseline, awaits CSD, writes I-frame, then launches gate; PTS = absoluteNanos − sessionStartNanos |
| 2026-05-25 | W-008 | `source-vision-telemetry` module — `VisionTelemetrySource` implementing `PlcTelemetrySource` via LLM optical gauge reading; `HollowellChannelPresets` (motor_rpm, screw_rpm, melt_temp_f, line_speed_fpm); `PlcTelemetrySource` + `LlmClient` interfaces added to `core-schema`; 11 unit tests (routing, gearbox transform, LLM parse failure, snapshotAt) | Unblocks R_phys extraction without PLC API; screw_rpm = motor_rpm ÷ 20.0 transform enforced at data level |
| 2026-05-25 | W-007 | `core-codec` module — `Mp4RealMuxer` interface, `AndroidMp4RealMuxer`, `Mp4RealWriter`, `EpsSyncMeasure`, `SessionMetadata` + sidecar writer; 23 JVM unit tests | Standard MPEG-4 container (fMP4 streaming upgrade is Phase 2); metadata tracks use text/vtt API 26+ safe |
| 2026-05-24 | — | April 8 PIE demo seed event ingested — corpus_depth=1, `material_segregation_funnel_flow`, graph_weight=0.88, 2 shadow_actions, KNOWLEDGE-level | `backend/api/corpus/events/6ab2942f-...json` |
| 2026-05-24 | — | MCP server wired into Claude Code via `.claude/settings.json`; `corpus_dir` resolved relative to `config.toml` (not CWD) | Server invocable from any working directory |
| 2026-05-24 | — | MCP server Session 001 — `arcshield/schema.py`, `CorpusBackend` ABC, `JsonCorpusBackend`, `server.py` (7 tools), 28/28 contract tests | Built prior to this repo; unpacked from `tools/arcshield-mcp-v1.zip` |
| 2026-05-25 | W-006 | `shadow-mode-labeler` Android module — `CandidateWindowLog` (NDJSON logger), `NonMaxSuppressor` (60 s bucket-max), `TauCalibrator`, `LabelerViewModel`, `LabelerScreen` / `CandidateWindowRow` / `TauSummaryCard`; `@Serializable` on `CandidateWindow`; kotlinx.serialization + Compose BOM + lifecycle-viewmodel-compose added to version catalog; 2 JVM test classes (TauCalibratorTest, NonMaxSuppressorTest); pointer README in tools/shadow-mode-labeler/ | Acceptance criterion ticked; wire into :app when app module scaffolded |
| 2026-05-25 | W-005 | `source-camerax` module — `CameraXCaptureSource` implementing `CaptureSource` (CameraX ImageAnalysis NV21 + AudioRecord 48kHz mono); `FrameDiffMotion` (Y-plane MAD with subsample=4); `Λ_motion` Gaussian-shift GLR wired in `LlrGate`; motion baseline fields added to `LlrBaseline`; `buildBaseline()` accepts `videoFrames` flow; CameraX 1.3.4 added to version catalog; 8 `FrameDiffMotionTest` unit tests; unsigned byte handling verified | Λ_motion now live when `motionAvailable = true` in baseline; 0.0 fallback when no video source connected |
| 2026-05-25 | W-004 | `LlrBaselineBuilder` — `buildBaseline()` suspend function + `SpectralAccumulator` (Welford per-bin FFT mean/variance) + `computeRmssdStats()` (20-beat sub-window RMSSD variance) + Welford online accel-RMS stats; injectable clock for JVM testability; 13 unit tests with `runTest` + finite flows | Timeout-based collection works for both production (infinite sensor flows cancel at durationMs) and tests (finite flows complete naturally) |
| 2026-05-25 | W-003 | `core-llr` Λ_bio — `RollingBioStats` (rolling HR mean/variance + RMSSD), activity gate (resting/light/moderate/vigorous), HR-delta GLR + RMSSD-deviation GLR wired into `llrGate()`; 10 unit tests; `activityGate` field added to `CandidateWindow`; bio fields added to `LlrBaseline`; `hrSamples`/`rrSamples` optional params on `llrGate()` | Nonlinear HRV (SD1/SD2, sample entropy) stubbed as `lambdaHrvNl = 0f` — Phase 2 after H10 ECG path live |
| 2026-05-24 | W-002 | `core-llr` Λ_env gate — `RealFft` (Cooley-Tukey), `KlDivergence`, `RollingAccelRms` (Welford), `LlrBaseline`, `LlrConfig`, `CandidateWindow`, `llrGate()` in shadow mode; 3 unit test classes | Λ_motion + Λ_gaze = 0.0 stubs; Λ_bio = 0.0 placeholder wired for next item |

---

## 6. Open Decisions Awaiting Author Sign-off

Architecture-level questions where Claude Code should **not** decide unilaterally. Park them here; do not implement until Kahn responds.

| Decision needed | Options on the table | Claude Code's recommendation | Date raised |
|---|---|---|---|
| [MOD-008] `sensor_readings` passed to `query_by_cause_signature` as a JSON string (MCP scalar constraint). Is this acceptable for agent consumption or should a structured prompt template wrap the call? | (a) keep JSON string injection — simple, works now / (b) add a prompt template helper that builds the JSON string for agents / (c) wait for MCP structured parameter support | (a) for now — the tool works correctly and agents handle JSON string injection fine. Revisit when corpus has real events and agents are actively querying. | 2026-05-24 |
| [MOD-007] `get_divergent_chains` as an 8th MCP tool: add the stub now (returning NOT_AVAILABLE) or wait until `GraphCorpusBackend` (Phase 3) is built? | (a) add stub now so the tool surface is declared early / (b) add only when GraphCorpusBackend exists | (a) add stub now — agents can see it exists and handle NOT_AVAILABLE gracefully. Zero implementation cost. | 2026-05-24 |
| [MOD-006] `graph_weight` counterfactual policy (A13 gap): Leiden community re-detection when a high-centrality event's weight changes by > 0.2. Confirm threshold and trigger condition before Phase 3 implementation. | (a) threshold 0.2 + top-10% betweenness / (b) different threshold / (c) defer entirely to Phase 4 | Defer to Phase 3 design session — need real corpus data to calibrate centrality thresholds. | 2026-05-24 |
| BiometricSource implementation when both H10 and Verity Sense are paired simultaneously | (a) prefer H10 for ECG, accept Verity Sense accel only if H10 absent / (b) prefer Verity Sense / (c) require explicit selection | (a) — mirrors validation-vs-operational device strategy in CLAUDE.md §8.3. | 2026-05-24 |

---

## 7. Drift Watch

Things observed during recent sessions that **could** become problems if they continue. Not yet blocking. Re-evaluate weekly.

- _Nothing currently flagged._

Common patterns to watch for (delete this once seen at least once, since at that point it's documented above):

- LLR gate accumulating implicit state that survives shift boundaries — should be I-frame-bounded.
- BLE reconnect emitting interpolated values instead of `BiometricGap` markers.
- `R_phys` deferred queue items quietly expiring without their events being marked `INDETERMINATE`.
- Codebook dict entries being mutated in place rather than versioned.
- Confidence updates running on a path that doesn't reference a real R_phys row.

---

## 8. Session Log

Append-only. One entry per Claude Code session or per Kahn working session. Keep entries short — full reasoning belongs in commits and §1 working notes.

```
YYYY-MM-DD — Kahn / Claude Code session #N
  - What was worked on: …
  - What changed in state above: …
  - Surprises: …
  - Next session pickup point: …
```

```
2026-MM-DD — Kahn — repo init
  - Committed CLAUDE.md and CURRENT_PHASE.md.
  - No code yet. Phase 1 clock starts when Polar SDK integration begins.
  - Next session pickup point: scaffold `source-polar` module and run first H10 bench test.
```

```
2026-05-24 — Claude Code — repo structure scaffolding
  - Created full directory tree per CLAUDE.md §10.
  - Moved CURRENT_PHASE.md → phases/CURRENT_PHASE.md (README already referenced this path).
  - Organized docs: whitepapers → docs/whitepaper/, patent drawings → docs/patent/, analysis docs → docs/.
  - Moved MCP zip archives → tools/.
  - Added .gitkeep to all empty module directories.
  - Next session pickup point: begin source-polar Kotlin module (BiometricSource interface, PMD integration).
```

```
2026-05-24 — Claude Code — MCP server unpacked + CURRENT_PHASE.md updated
  - Unpacked arcshield-mcp-v1.zip into backend/api/ (canonical source — has server.py, config.toml, SESSION_LOG.md).
  - arcshield-mcp-corpus-backend.zip is a subset of v1 (no server.py/config.toml) with compiled pycs; confirmed identical source; left as archive in tools/.
  - Session 001 work (previously done): arcshield/schema.py (full CIAER+ Pydantic models), CorpusBackend ABC (3-phase upgrade path), JsonCorpusBackend (Phase 1, flat JSON, 28/28 contract tests passing), server.py (7 MCP tools: list_failure_modes, query_by_failure_mode, query_by_cause_signature, get_event, get_shadow_actions, ingest_event, update_graph_weight).
  - Integrated all 9 MOD items and 3 open questions from SESSION_LOG.md into this file: MOD-005 (escalation_delta invariant) added top of backlog; MOD-007/008/006 raised to §6; corpus bootstrap added to §4.
  - Surprises: MOD-005 (escalation_delta coherence check) is listed in the CorpusBackend docstring as a MUST but is NOT implemented in JsonCorpusBackend — this is a CLAUDE.md §2.4 invariant violation. Priority item in backlog.
  - Next session pickup point: implement MOD-005 escalation_delta check in json_backend.py, then scaffold source-polar Kotlin module.
```

```
2026-05-24 — Claude Code — MCP wired + April 8 seed event ingested
  - Fixed MOD-002: load_config() now resolves corpus_dir relative to config.toml location, not CWD. Server invocable from any working directory.
  - Created .claude/settings.json (project-level) wiring arcshield MCP server into Claude Code. Args: python backend/api/server.py --config backend/api/config.toml.
  - Constructed and ingested April 8, 2026 PIE demonstration event (event_id 6ab2942f). failure_mode_tag=material_segregation_funnel_flow, escalation_state=2→0, KNOWLEDGE-level, 2 shadow_actions, voice_transcript captured, graph_weight=0.88. All schema invariants verified including escalation_delta=2.
  - Corpus depth: 0 → 1.
  - bootstrap_seed_event.py committed to backend/api/ — idempotent, safe to re-run.
  - corpus/events/ is committed (intentional for prior art provenance on the seed). Live captured events from the line should probably be gitignored — decide before first line capture.
  - Next session pickup point: discuss optimum path forward (Android source-polar vs. MCP refinements vs. corpus building).
```

```
2026-05-24 — Claude Code — BLOCKING elicitation + session close
  - Ran 5 BLOCKING questions from docs/ELICITATION_LOG.md with Kahn. All answered, rules promoted to docs/SELECTION_PRINCIPLE.md, questions marked [ANSWERED 2026-05-24].
  - Q2.1: BiometricSource — both H10 and Verity Sense supported equally from day one. Auto-detect; operator prompt if both paired.
  - Q3.1: operator_id — self-sovereign key, provenance only, zero privileges, portable.
  - Q4.1: θ_TC — fixed global from shadow-mode calibration (~80% TP), per-primitive field in schema from day one, per-primitive divergence is Phase 3+ data change.
  - Q6.1: arXiv before non-provisional. Non-provisional NOT YET FILED. Provisional priority date 2026-04-08. Window closes ~2027-04-08. Added to §4 blocked.
  - Q7.1: Corpus validation sprint — same line same shift different operator → primary operator on second extrusion line → different operator on second line. All within one week once recording begins.
  - Non-provisional patent deadline added to §4. This is the hardest external clock in the project.
  - Remaining elicitation questions: Q2.2, Q3.2, Q3.3, Q3.4, Q3.5, Q4.2, Q4.3, Q4.4, Q5.1, Q5.2, Q5.3, Q6.2, Q6.3, Q7.2, Q7.3, Q8.1, Q8.2, Q9.1, Q9.2, Q10.1, Q10.2 — all HIGH or lower, none BLOCKING.
  - Everything committed directly to main throughout the session. Branch claude/repo-structure-setup-mvrRI was merged to main early in the session; all subsequent work was committed directly to main.
  - Next session pickup point: (1) decide whether to run HIGH elicitation questions or shift to Android source-polar module; (2) if build path — scaffold source-polar Kotlin module with BiometricSource interface, PolarBleBiometricSource stub, auto-detection logic skeleton; (3) open question — should corpus/events/ be gitignored for live captures while keeping the seed event tracked?
```

```
2026-05-24 — Claude Code — source-polar scaffold
  - Confirmed MOD-005 already implemented (json_backend.py lines 191–201) and tested — session log was stale. No action needed.
  - Created Android Gradle infrastructure: android/settings.gradle.kts, android/build.gradle.kts, android/gradle/libs.versions.toml, android/gradle/wrapper/gradle-wrapper.properties.
  - Created core-schema module: BiometricSource interface, BiometricChannel enum, GapReason enum, BiometricGap, HrSample, RrSample, EcgSample, AccelSample, EdaSample. All shared types — no Polar SDK dependency in core-schema.
  - Created source-polar module: PolarDeviceType enum (H10 / VERITY_SENSE with capabilities, sourceId, supportsOfflineRecording), PolarBleBiometricSource implementing full BiometricSource interface.
  - Key invariants implemented: elapsedRealtimeNanos clock anchor on first PMD frame, per-sample timestamp reconstruction from frame-last-sample + index arithmetic, explicit BiometricGap emission on BLE dropout (never silently interpolated), lowSyncConfidence flag on gaps >= 4s, exponential reconnect backoff (2s→30s cap), H10 offline recording backfill stubbed with TODO.
  - Dependency graph: core-capture → core-schema ← source-polar (consumers never reference concrete Polar classes).
  - Next session pickup point: wire core-llr Λ_env (acoustic spectral KL divergence) — next Phase 1 deliverable.
```

```
2026-05-24 — Claude Code — core-llr Λ_env gate + shadow mode
  - Added AudioFrame, VideoFrame, CaptureSource interface to core-schema (capture package).
  - Created core-llr module: LlrBaseline, LlrConfig, CandidateWindow, llrGate().
  - Internal math: RealFft (Cooley-Tukey radix-2 DIT, pure Kotlin, no deps), KlDivergence (epsilon-smoothed KL(P||Q)), RollingAccelRms (Welford online variance, circular buffer).
  - LlrGate uses channelFlow with three concurrent coroutines: audio collector, accel collector, eval ticker.
  - Λ_acoustic = KL(baseline_spectrum || rolling_spectrum). Λ_accel = Gaussian-shift GLR.
  - Λ_motion, Λ_gaze, Λ_bio all 0.0 with explicit stub comments. shadowMode = true default.
  - 3 unit test classes (12 tests): RealFftTest, KlDivergenceTest, RollingStatsTest.
  - Added junit + kotlinx-coroutines-test to libs.versions.toml.
  - Next session pickup point: wire Λ_bio (HR delta + HRV-RMSSD) in core-llr.
```

```
2026-05-25 — Claude Code — LlrBaselineBuilder
  - Created LlrBaselineBuilder.kt: buildBaseline() suspend function collects all sensor flows concurrently for durationMs (default 90s) using withTimeoutOrNull + coroutineScope.
  - SpectralAccumulator: Welford per-bin online mean and variance for acoustic power spectra. Falls back to uniform distribution when no frames received.
  - computeRmssdStats(): overall RMSSD from full RR sequence + sub-window variance from 20-beat windows (stride 10). Gives ~10 variance estimates from a 90s I-frame at typical resting HR.
  - Welford online accumulator for accel RMS time series (mean + variance of rolling-RMS values, not raw samples).
  - Injectable clock: `clock: () -> Long = { SystemClock.elapsedRealtimeNanos() }` — avoids Android stubs in JVM tests without polluting public API.
  - 13 unit tests in LlrBaselineBuilderTest using runTest + finite flows (no virtual time advancement needed).
  - Next session pickup point: scaffold source-camerax module (CaptureSource implementation) and wire Λ_motion (frame-to-frame video energy) in LlrGate.
```

```
2026-05-25 — Claude Code — W-007: core-codec module
  - Created android/core-codec/ module registered in settings.gradle.kts (already present).
  - Mp4RealConfig: W_pre (30s), W_post (60s), iFrameDurationMs (90s), outputDir, facilityId, lineId — all tunable per facility.
  - TrackType sealed class: 7 tracks (Video, Audio, AccelMeta, BiometricMeta, ThermalMeta, VoiceMeta, PlcTelemetryMeta). Phase 1 = first 5.
  - EncodedSample / MetaSample: timestamped ByteArray wrappers; timestamps in elapsedRealtimeNanos throughout; µs conversion only at MediaMuxer boundary.
  - EpsSyncMeasure: measure() + combine(); TARGET_NS=100ms, LOW_SYNC_NS=250ms; pure JVM, no Android deps.
  - SessionMetadata: @Serializable; writeSidecar() writes {stem}.mp4real.json alongside container. Phase 1 substitute for udta box (Phase 2 upgrade via mp4parser).
  - Mp4RealMuxer interface: addVideoTrack / addAudioTrack / addMetaTrack / start / writeSample / writeMetaSample / stop / release. Extracted for JVM testability.
  - AndroidMp4RealMuxer: MediaMuxer-backed production impl. MPEG-4 container (standard MP4; fMP4 streaming upgrade is Phase 2). Metadata tracks use text/vtt + JSON-UTF-8 (API 26+ safe; avoids createSubtitleFormat() API 28 requirement). ByteBuffer grown on demand.
  - Mp4RealWriter: orchestrator; addVideoTrack / addAudioTrack / addMetaTrack before start(); writeSample / writeMetaSample after start(); close() stops muxer, writes sidecar, returns final SessionMetadata with ε_sync + track map. Core-capture is responsible for ring buffer windowing — this writer accepts whatever samples it receives.
  - 10 EpsSyncMeasureTest + 13 Mp4RealWriterTest (FakeMuxer) = 23 JVM unit tests. Android SDK not present in cloud environment; run ./gradlew :core-codec:test on a machine with SDK.
  - Next session pickup point: build core-capture (ring buffer + H.265/AAC encoding via MediaCodec + mux pipeline that connects LLR gate → Mp4RealWriter).
```

```
2026-05-25 — Claude Code — W-006: shadow-mode-labeler
  - Added kotlinx.serialization 1.7.3, Compose BOM 2024.09.00, lifecycle-viewmodel-compose 2.8.4, activity-compose 1.9.1, material-icons-core to libs.versions.toml.
  - Added kotlin-serialization + kotlin-compose plugins to version catalog.
  - Added @Serializable to CandidateWindow in core-llr (+ serialization plugin + dep in core-llr/build.gradle.kts).
  - Created android/shadow-mode-labeler/ module registered in settings.gradle.kts:
      CandidateWindowLog: NDJSON appender + reader, mutex-protected, crash-safe flush, skips malformed lines
      NonMaxSuppressor: O(n log n) bucket-max reducing 57,600 ticks to ≤480 candidate events
      TauCalibrator: pure JVM; suggests τ for ≥80% TP / ≤20% FP; falls back to τ=0 if FP constraint unsatisfiable
      LabelerViewModel: AndroidViewModel; shift log list, NMS toggle, label state, export to .labels.json
      LabelerScreen / LabelingPane / ShiftPickerPane / CandidateWindowRow / TauSummaryCard: Compose UI
  - 2 JVM test classes: TauCalibratorTest (8 cases), NonMaxSuppressorTest (5 cases).
  - tools/shadow-mode-labeler/README.md pointer (replaced .gitkeep).
  - Android SDK not present in cloud environment; tests verified by inspection. Run ./gradlew :shadow-mode-labeler:test on a machine with SDK to confirm.
  - Next session pickup point: bench-test PolarBleBiometricSource against H10 (backlog item #1), OR scaffold :app module and wire labeler as debug screen.
```

```
2026-05-25 — Claude Code — W-005: source-camerax + Λ_motion
  - Merged origin/main (310d2ae MCP consolidation) into feature branch before starting.
  - Created android/source-camerax/ module: build.gradle.kts (CameraX 1.3.4), AndroidManifest (CAMERA + RECORD_AUDIO permissions), CameraXCaptureSource.kt.
  - CameraXCaptureSource: videoFrames() via CameraX ImageAnalysis (STRATEGY_KEEP_ONLY_LATEST, YUV_420_888 → NV21 via ImageProxy.toVideoFrame()); audioFrames() via AudioRecord 48kHz mono 480-sample buffers (elapsedRealtimeNanos timestamps on all frames).
  - Created FrameDiffMotion (core-llr/internal): Y-plane MAD with subsample=4; null on first frame; unsigned byte arithmetic (0xFF → 255, not -1); reset() clears prev frame.
  - Updated LlrBaseline: added motionBaselineMad, motionVarianceMad, motionAvailable (all defaulted — backward compatible with existing tests).
  - Updated LlrGate: added optional videoFrames flow (default emptyFlow); video producer coroutine; Λ_motion Gaussian-shift GLR; 0.0 when motionAvailable = false.
  - Updated LlrBaselineBuilder: added videoFrames param; Welford online MAD accumulator; motion fields in returned LlrBaseline.
  - Added CameraX 1.3.4 to libs.versions.toml (camerax-core, camerax-camera2, camerax-lifecycle).
  - 8 unit tests in FrameDiffMotionTest: first-frame null, identical frames → 0, known uniform diff, subsample invariance on uniform frames, reset behavior, unsigned byte handling.
  - Next session pickup point: build tools/shadow-mode-labeler (minimal Compose screen) OR bench-test source-polar + source-camerax on device.
```

```
2026-05-25 — Claude Code — core-llr Λ_bio wired
  - Created RollingBioStats (internal): rolling HR window (time-based, 5-min) → mean + variance; rolling RR window → RMSSD. Mutex-protected in LlrGate since HR and RR producers run concurrently.
  - Updated LlrBaseline: added hrBaselineBpm, hrVarianceBpm, rmssdBaselineMs, rmssdVarianceMs, biometricAvailable (all defaulted — backward compatible).
  - Updated LlrConfig: added activity gate thresholds (lightAccelThresholdMg, moderateAccelThresholdMg, vigorousAccelThresholdMg) and gate factors.
  - Updated CandidateWindow: added activityGate field for shadow-mode labeler diagnostics.
  - Updated llrGate(): added optional hrSamples + rrSamples flows (default emptyFlow — backward compatible). HR producer + RR producer coroutines added. Λ_hr (Gaussian-shift GLR) + Λ_rmssd (Gaussian-shift GLR) + activity gate computed in eval ticker. lambdaHrvNl stubbed 0f (nonlinear HRV — Phase 2).
  - New test class: RollingBioStatsTest — 10 tests covering HR mean/variance, RMSSD formula, rolling eviction, reset.
  - Existing 3 test classes unaffected (no CandidateWindow or LlrBaseline construction in those tests).
  - Next session pickup point: implement LlrBaselineBuilder (90-second I-frame accumulator for all channels), then wire source-camerax (Λ_motion stub → live).
```

```
2026-05-25 — Claude Code — W-008: source-vision-telemetry module
  - Added PlcTelemetrySource + TelemetrySample + TelemetrySnapshot to core-schema (telemetry package).
  - Added LlmClient + SensoryContext + ElicitationPrompt + ElicitationResponse + GuidanceQuery + TwinGuidance to core-schema (llm package). Phase 1 minimal types; full elicitation wired in Phase 3.
  - Created android/source-vision-telemetry/ module registered in settings.gradle.kts.
  - VisionTelemetrySource: implements PlcTelemetrySource; retains latestFrame via AtomicReference; channel() emits Flow<TelemetrySample> at sampleIntervalMs polling; skips poll if no frame or if LLM returns null parsedValue; snapshotAt() reads all channels synchronously on the most recent frame; injectable clock for JVM testability.
  - HollowellChannelPresets: motor_rpm (identity transform), screw_rpm (÷20.0 gearbox), melt_temp_f, line_speed_fpm; ppvcLine1 preset config.
  - 11 JVM unit tests: availableChannels, unknown channelId → emptyFlow, frame present → emits sample, no frame → timeout, screw_rpm transform, motor_rpm identity, both channels diverge by ratio, null LLM → no sample, snapshotAt empty, snapshotAt all channels, snapshotAt null LLM → empty.
  - Run: ./gradlew :source-vision-telemetry:test on a machine with Android SDK.
  - docs/BRAINSTORM_HANDOFF.md committed.
  - Next session pickup point: build core-capture (W-009) — ring buffer + MediaCodec H.265/AAC encoding + mux pipeline connecting LLR gate fire → Mp4RealWriter.
```

```
2026-05-25 — Claude Code — W-009: core-capture module
  - Created android/core-capture/ module registered in settings.gradle.kts.
  - CaptureSessionConfig: all tunable params (videoWidth/Height, frameRateFps, bitrates, wPreMs, wPostMs, iFrameDurationMs, ringBufferCapacityMs, metaTracks). ringCapacityNanos / wPreNanos / wPostNanos as computed properties.
  - ChannelRingBuffer<T>: generic ring buffer with ReentrantReadWriteLock; offer() evicts items older than (newest_ts − capacityNanos); extract(start, end) returns sorted list. Single-sample buffer never evicts itself.
  - CapturedWindow: data class with five sample lists + isEmpty + sessionStartNanos/windowStart/windowEnd/isIFrame fields.
  - WindowExtractor: extracts CapturedWindow from 5 ring buffers by calling .extract(start, end). Boundary math: start = fireTime − wPre, end = fireTime + wPost.
  - EpsSyncCoordinator: recordSyncPoint() → EpsSyncMeasure.measure(); currentSync() → combine(); isSyncDue() uses injectable clock vs syncIntervalMs; reset() clears state.
  - VideoEncoderDelegate + AudioEncoderDelegate: interfaces exposing Flow<EncodedSample>, Deferred<ByteArray> csd0, start(), suspend encode(), release().
  - MediaCodecVideoEncoder: HEVC async callback encoder; onInputBufferAvailable feeds from Channel<VideoFrame>; onOutputFormatChanged completes csd0 Deferred; CSD-config packets skipped.
  - MediaCodecAudioEncoder: AAC async callback encoder; ShortArray → little-endian byte PCM conversion on input; same callback pattern.
  - CaptureSession: start() suspends for iFrameDurationMs while building LlrBaseline; awaits csd0 from both encoders; adds tracks to Mp4RealWriter; writes I-frame; launches gate coroutine. muxWindow() subtracts sessionStartNanos from all PTS before writing. candidateWindows SharedFlow exposed for shadow-mode labeler. ε_sync periodic resync triggered from HR flow.
  - 8 ChannelRingBufferTest + 5 WindowExtractorTest + 7 EpsSyncCoordinatorTest = 20 JVM unit tests.
  - Phase 1 acceptance: core-capture ✅ core-codec ✅ core-llr ✅. Remaining: device tests (ε_sync, shadow-mode 20–30 windows), source-polar bench test, :app wiring.
  - Next session pickup point: scaffold :app module + Hilt DI, wire CaptureSession end-to-end, OR implement ClaudeVisionClient in llm-claude to make VisionTelemetrySource functional.
```

```
2026-05-25 — Claude Code — W-010: llm-claude — ClaudeVisionClient
  - Added okhttp 4.12.0 + okhttp-mockwebserver to libs.versions.toml.
  - Created android/llm-claude/ module (was empty stub).
  - VideoFrameEncoder: NV21 YuvImage → compressToJpeg(quality=85) → Base64.NO_WRAP. Kept internal; only ClaudeVisionClient uses it.
  - ClaudeVisionClient: implements LlmClient. Constructor accepts apiKey, model (default claude-haiku-4-5-20251001), maxTokens, httpClient (injectable for tests). elicit() → buildRequestJson() + post() + extractTextContent() + parseGaugeValue(). generateGuidance() returns Phase 3 stub.
  - buildRequestJson(): builds Anthropic Messages API JSON; image block only present when SensoryContext.frame != null; uses kotlinx.serialization buildJsonObject/buildJsonArray DSL.
  - post(): OkHttpClient.execute() on Dispatchers.IO; throws IOException on non-2xx; sets x-api-key, anthropic-version headers.
  - parseGaugeValue(): fast path toDoubleOrNull(), then Regex(-?\d+(?:\.\d+)?) for embedded numbers; returns null on unreadable/NA/blank.
  - 12 unit tests: 10 parseGaugeValue cases + JSON parse verification + buildRequestJson no-frame case (no image block). MockWebServer declared for future HTTP integration tests.
  - API key never in source — must be injected via BuildConfig or local config in :app module.
  - Next session pickup point: scaffold :app module + Hilt DI wiring all sources together.
```

---

## 10. Backend / MCP Server State (backend/api/)

Single-source summary of the Python backend and MCP corpus server. Full implementation specs for deferred MOD items are in `backend/api/SESSION_LOG.md` — read that file when actually implementing a MOD item, not just triaging.

### Current build state (as of 2026-05-24)

| File | Status | Notes |
|---|---|---|
| `arcshield/schema.py` | ✅ built | Full CIAER+ Pydantic v2 models |
| `arcshield/corpus/backend.py` | ✅ built | Abstract `CorpusBackend` — 3-phase upgrade path |
| `arcshield/corpus/backends/json_backend.py` | ✅ built | Phase 1 flat-file backend |
| `arcshield/corpus/backends/__init__.py` | ✅ built | `get_backend()` factory — one-line config swap |
| `server.py` | ✅ built | FastMCP stdio server, lifespan-managed, `config.toml` loading |
| `config.toml` | ✅ built | `facility_id`, backend type, `allow_writes` |
| `tests/test_corpus_backend_contract.py` | ✅ 28/28 passing | Parameterized contract suite; new backends auto-tested by adding to fixture |

**Run tests:** `cd backend/api && python -m pytest tests/ -v --asyncio-mode=auto`

### 7 MCP tools (server.py)

| Tool | Purpose |
|---|---|
| `list_failure_modes` | All failure mode tags with event counts |
| `query_by_failure_mode` | Events matching a tag, ordered by graph_weight |
| `query_by_cause_signature` | Sensor-signature similarity search (Phase 1: instrument-coverage score) |
| `get_event` | Full CIAER+ record by event_id |
| `get_shadow_actions` | Rejected alternatives for an event |
| `ingest_event` | Write a new CIAER+ event (requires `allow_writes = true`) |
| `update_graph_weight` | Adjust graph_weight with audit trail |

### Corpus state

| Field | Value |
|---|---|
| Depth | 1 event |
| Seed event | `6ab2942f` — April 8, 2026 PIE demo — `material_segregation_funnel_flow` |
| graph_weight | 0.88 |
| SRK level | KNOWLEDGE |
| Shadow actions | 2 |
| corpus/events/ | Committed to git (intentional — prior art provenance on the seed) |

**Open question:** should `corpus/events/` be gitignored for live line captures while keeping the seed event tracked? Decide before first on-line capture session.

### Deferred MOD items (Phase 2–3)

Phase 2 items are wired when corpus reaches scale. Phase 3 items require `GraphCorpusBackend`.

| ID | Phase | Trigger | Summary |
|---|---|---|---|
| MCP-MOD-001 | 2 | corpus_depth > ~500 OR latency > 200ms | `SqliteCorpusBackend` — see §3 item 6 |
| MCP-MOD-003 | 2→3 | Phase 2 | `query_by_cause_signature` value-proximity scoring — see §3 item 7 |
| MCP-MOD-004 | 2 | Phase 2 | Per-operator write auth — see §3 item 8 |
| MCP-MOD-006 | 3 | Phase 3 | `graph_weight` counterfactual policy — Leiden re-detection on high-centrality weight changes > 0.2. Threshold needs real corpus data to calibrate — see §6 open decisions. |
| MCP-MOD-007 | 3 | Phase 3 | `get_divergent_chains` 8th MCP tool stub — see §3 item 9 |
| MCP-MOD-002 | 3 | corpus_depth > ~5000 OR multi-facility | `GraphCorpusBackend` (Neo4j) — native Cypher, HNSW ANN, CIAER-QL. Full spec in `SESSION_LOG.md`. |
| MCP-MOD-008 | 3 | Phase 3 | Android → MCP ingest bridge — `McpCorpusSink.kt` in Android app (cross-repo, not in this repo) |

---

## 9. Update Protocol for Claude Code

At the end of every working session in this repo:

1. Move any newly completed items from §1 to §5.
2. Update §2 acceptance checkboxes against actual state — be honest, don't tick anything that isn't truly done.
3. If anything new is blocked, add it to §4 with the unblock condition.
4. If an architectural question came up that you couldn't resolve from CLAUDE.md, add it to §6 — do not guess and implement.
5. Add a one-paragraph entry to §8 with the next session's pickup point.
6. Update §0 corpus depth, codebook size, and "Last working session" line.
7. Update the "Build is" status indicator in §0 based on the state of §4 (any 🔴 blockers? → 🔴; any 🟡 flags in §7? → 🟡; clean? → 🟢).
8. Commit this file in the same commit as the code change it documents.

**Never delete from §5 or §8.** Truncate §5 to 10 entries by moving older entries into a `phases/archive/` subdirectory if needed, but do not lose history.

---

*End of CURRENT_PHASE.md. State only. Architecture lives in /CLAUDE.md.*
