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
| **Last working session** | 2026-05-25 — Kahn / Claude Code — Architectural pivot to optical VisionTelemetrySource to unblock R_phys extraction |
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

- [ ] `core-capture`, `core-codec`, `core-llr` modules functional
- [ ] `source-polar` (H10) consuming PMD streams
- [x] `source-camerax` POV video
- [ ] Phone-side mux pipeline writing fMP4 with all five Phase 1 tracks (POV, acoustic, vibration, biometric, voice)
- [ ] LLR gate operates in shadow mode (logs candidates, never persists a container)
- [ ] ε_sync measured and logged per session
- [ ] ε_sync sustained ≤ 100 ms across at least 5 production shifts
- [ ] 20–30 candidate windows logged
- [x] Hand-labeling tool (`tools/shadow-mode-labeler`) operational
- [ ] All BLE dropouts emit explicit `BiometricGap` records (no silent interpolation)

**Phase 1 hard stop:** if ε_sync exceeds 250 ms persistently, halt and debug Polar↔phone clock anchor before proceeding. Document the failure mode here:

_None observed yet._

---

## 3. Backlog (Next Up)

Ordered by intended pickup, not by priority alone. Top of list is next.

1. **[W-007] Scaffold `source-vision-telemetry` module** — Architectural pivot to bypass the blocked PLC integration. Implement `VisionTelemetrySource` conforming to the `PlcTelemetrySource` interface. Wire `CameraXCaptureSource` frame extraction triggered by `gaze_dwell` to pass frames to `LlmClient`. Implement parsing logic to coerce LLM output into the `TelemetrySample` schema. Include required mathematical transformations in the extraction pipeline; specifically, optical readouts of motor RPM must be divided by the 20:1 gearbox ratio to record actual screw RPM in the graph.
2. **Bench-test `PolarBleBiometricSource` against H10** over a 4-hour continuous capture; characterize dropout rate near the extruder barrel.
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
| 2026-05-24 | — | April 8 PIE demo seed event ingested — corpus_depth=1, `material_segregation_funnel_flow`, graph_weight=0.88, 2 shadow_actions, KNOWLEDGE-level | `backend/api/corpus/events/6ab2942f-...json` |
| 2026-05-24 | — | MCP server wired into Claude Code via `.claude/settings.json`; `corpus_dir` resolved relative to `config.toml` (not CWD) | Server invocable from any working directory |
| 2026-05-24 | — | MCP server Session 001 — `arcshield/schema.py`, `CorpusBackend` ABC, `JsonCorpusBackend`, `server.py` (7 tools), 28/28 contract tests | Built prior to this repo; unpacked from `tools/arcshield-mcp-v1.zip` |
| 2026-05-25 | W-006 | `shadow-mode-labeler` Android module — `CandidateWindowLog` (NDJSON logger), `NonMaxSuppressor` (60 s bucket-max), `TauCalibrator`, `LabelerViewModel`, `LabelerScreen` / `CandidateWindowRow` / `TauSummaryCard`; `@Serializable` on `CandidateWindow`; kotlinx.serialization + Compose BOM + lifecycle-viewmodel-compose added to version catalog; 2 JVM test classes (TauCalibratorTest, NonMaxSuppressorTest); pointer README in tools/shadow-mode-labeler/ | Acceptance criterion ticked; wire into :app when app module scaffolded |
| 2026-05-25 | W-005 | `source-camerax` module — `CameraXCaptureSource` implementing `CaptureSource` (CameraX ImageAnalysis NV21 + AudioRecord 48kHz mono); `FrameDiffMotion` (Y-plane MAD with subsample=4); `Λ_motion` Gaussian-shift GLR wired in `LlrGate`; motion baseline fields added to `LlrBaseline`; `buildBaseline()` accepts `videoFrames` flow; CameraX 1.3.4 added to version catalog; 8 `FrameDiffMotionTest` unit tests; unsigned byte handling verified | Λ_motion now live when `motionAvailable = true` in baseline; 0.0 fallback when no video source connected |
| 2026-05-25 | W-004 | `LlrBaselineBuilder` — `buildBaseline()` suspend function + `SpectralAccumulator` (Welford per-bin FFT mean/variance) + `computeRmssdStats()` (20-beat sub-window RMSSD variance) + Welford online accel-RMS stats; injectable clock for JVM testability; 13 unit tests with `runTest` + finite flows | Timeout-based collection works for both production (infinite sensor flows cancel at durationMs) and tests (finite flows complete naturally) |
| 2026-05-25 | W-003 | `core-llr` Λ_bio — `RollingBioStats` (rolling HR mean/variance + RMSSD), activity gate (resting/light/moderate/vigorous), HR-delta GLR + RMSSD-deviation GLR wired into `llrGate()`; 10 unit tests; `activityGate` field added to `CandidateWindow`; bio fields added to `LlrBaseline`; `hrSamples`/`rrSamples` optional params on `llrGate()` | Nonlinear HRV (SD1/SD2, sample entropy) stubbed as `lambdaHrvNl = 0f` — Phase 2 after H10 ECG path live |
| 2026-05-24 | W-002 | `core-llr` Λ_env gate — `RealFft` (Cooley-Tukey), `KlDivergence`, `RollingAccelRms` (Welford), `LlrBaseline`, `LlrConfig`, `CandidateWindow`, `llrGate()` in shadow mode; 3 unit test classes | Λ_motion + Λ_gaze = 0.0 stubs; Λ_bio = 0.0 placeholder wired for next item |
| 2026-05-24 | W-001 | `source-polar` Kotlin module scaffolded — `BiometricSource` interface + sample types in `core-schema`, `PolarBleBiometricSource` + `PolarDeviceType` in `source-polar`, full Gradle infra (settings, version catalog, wrapper) | Clock anchoring, gap emission, reconnect backoff, H10 offline recording stub all implemented |
| 2026-05-24 | — | MOD-005 `escalation_delta` coherence check — already implemented in `json_backend.py` lines 191–201 and tested in `TestIngest`; session log was stale | No action required |
| 2026-05-24 | W-000 | Repository scaffolded per CLAUDE.md §10 — full directory tree, files organized, CURRENT_PHASE.md moved to phases/ | Initial structure commit |

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

