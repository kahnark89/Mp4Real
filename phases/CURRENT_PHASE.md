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
| **Last working session** | 2026-05-25 — Claude Code — core-llr: LlrBaselineBuilder (90-second I-frame accumulator, all channels, 13 unit tests) |
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
- [ ] `source-camerax` POV video
- [ ] Phone-side mux pipeline writing fMP4 with all five Phase 1 tracks (POV, acoustic, vibration, biometric, voice)
- [ ] LLR gate operates in shadow mode (logs candidates, never persists a container)
- [ ] ε_sync measured and logged per session
- [ ] ε_sync sustained ≤ 100 ms across at least 5 production shifts
- [ ] 20–30 candidate windows logged
- [ ] Hand-labeling tool (`tools/shadow-mode-labeler`) operational
- [ ] All BLE dropouts emit explicit `BiometricGap` records (no silent interpolation)

**Phase 1 hard stop:** if ε_sync exceeds 250 ms persistently, halt and debug Polar↔phone clock anchor before proceeding. Document the failure mode here:

_None observed yet._

---

## 3. Backlog (Next Up)

Ordered by intended pickup, not by priority alone. Top of list is next.

1. **Bench-test `PolarBleBiometricSource` against H10** over a 4-hour continuous capture; characterize dropout rate near the extruder barrel.
2. ~~**Wire `core-llr` Λ_bio** from HR delta + HRV-RMSSD ratio — Polar PMD-derived.~~ **DONE 2026-05-25** — see §5.
3. **Implement nonlinear HRV (SD1/SD2 + sample entropy) in `core-llr`** — Phase 2 once H10 ECG is live. Currently stubbed as `lambdaHrvNl = 0f` in `LlrGate.kt`.
6. **Build `tools/shadow-mode-labeler`** as a minimal Compose screen reading candidate windows from local storage.
7. **Implement `PreEnvSource.captureBaseline()`** as a 90-second sample-all-channels routine triggered manually at shift start (auto-detection deferred to Phase 2).
8. **[MOD-002] Fix `corpus_dir` path resolution in `load_config()`** — resolve relative to `config.toml` location, not CWD. Prevents breakage when `server.py` is invoked from a different directory. File: `backend/api/server.py`, `load_config()`.
9. **[MOD-001] `SqliteCorpusBackend`** — Phase 2 trigger: corpus_depth > ~500 events or `list_failure_modes` scan latency > 200ms. See `backend/api/SESSION_LOG.md` MOD-001 for full spec.
10. **[MOD-004] Per-operator write auth** — Phase 2. `config.toml [auth] write_operators` allowlist + signed token verification in `ingest_event` and `update_graph_weight`. See `backend/api/SESSION_LOG.md` MOD-004.
11. **[MOD-003] `query_by_cause_signature` similarity upgrade** — Phase 2: value-proximity weighting; Phase 3: embedding ANN. See `backend/api/SESSION_LOG.md` MOD-003 for update checklist.

When pulling an item from this list into §1, copy its text verbatim and assign a W-### ID.

---

## 4. Blocked / Flagged

Items that cannot advance until something external resolves. Each entry needs an unblock condition.

| Item | Blocked on | Unblock condition | First flagged | Last poked |
|---|---|---|---|---|
| Non-provisional patent filing | Patent counsel + budget | File before provisional expires (prior art priority date: 2026-04-08 — 12-month window closes ~2027-04-08). arXiv submission precedes non-provisional per Q6.1 rule. | 2026-05-24 | 2026-05-24 |
| Facility deployment agreement with Hollowell | Legal sign-off | Signed MSA + data-rights addendum | 2026-05-24 | 2026-05-24 |
| PLC API integration (Phase 3 prep) | Vendor access + IT scope clarification | Read-only OPC-UA endpoint or documented historian export | 2026-05-24 | 2026-05-24 |
| ~~[MOD-009] Corpus bootstrap~~ | ~~Facility agreement + active capture~~ | **DONE** — seed event 6ab2942f ingested 2026-05-24 | 2026-05-24 | 2026-05-24 |

---

## 5. Recently Completed

Last 10 items max. Anything older lives in version control.

| Date | ID | Item | Notes |
|---|---|---|---|
| 2026-05-24 | — | April 8 PIE demo seed event ingested — corpus_depth=1, `material_segregation_funnel_flow`, graph_weight=0.88, 2 shadow_actions, KNOWLEDGE-level | `backend/api/corpus/events/6ab2942f-...json` |
| 2026-05-24 | — | MCP server wired into Claude Code via `.claude/settings.json` (project-level); MOD-002 path resolution fix in `load_config()` | Server invocable from any CWD |
| 2026-05-24 | — | MCP server (Session 001) — `arcshield/schema.py`, `CorpusBackend` ABC, `JsonCorpusBackend`, `server.py` (7 tools), contract test suite (28/28 passing) | Built in prior session; unpacked from zip into `backend/api/` |
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
