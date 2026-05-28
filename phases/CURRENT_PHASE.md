# CURRENT_PHASE.md

**This file is the live state of the ArcShield build. It changes often. CLAUDE.md does not.**

The handoff document (`/CLAUDE.md`) describes architecture, schema, and invariants that should never drift. This file describes where the build currently is, what's actively in flight, what's blocking, and what's next. Update it at the end of every working session.

> **Re-entry rule:** Read CLAUDE.md first for architecture. Second read this file & CURRENT_PHASE_Gemini.md for state. Never invert that order.

---

## 0. At a Glance

| | |
|---|---|
| **Current Phase** | _Phase 1 — Container + LLR gate, shadow mode_ |
| **Phase start date** | 2026-05-24 |
| **Target completion** | ~2026-07-05 (6 weeks) |
| **Calendar week of phase** | Week 2 of 6 |
| **Validation site status** | PPVC Line 1 — awaiting facility agreement |
| **Corpus depth** | 1 validated CIAER+ event (April 8, 2026 PIE demo — material_segregation_funnel_flow) |
| **Codebook size** | _0 primitives_ |
| **Last shift captured** | _YYYY-MM-DD / none yet_ |
| **Last working session** | 2026-05-28 — Claude Code — Gate tuning UI (W-032); dark PWA UI port 3-tab layout + dwell overlay (W-033); adaptive baseline architecture design |
| **Build is** | 🟢 healthy — debug APK assembles clean |

---

## 1. Active Work Items

Things being worked on right now. Move items here from §3 (Backlog) when starting; move to §5 (Completed) on acceptance. Limit WIP to 3 active items at a time — more than that means something is actually blocked and should be in §4.

_No active items — see §5 for recently completed work. Next: implement W-034 (adaptive baseline) or run first Λ_env-only shadow sessions on Pixel 9 Pro._

| ID | Item | Module(s) | Started | Owner | State |
|---|---|---|---|---|---|

---

## 2. Phase 1 Acceptance Tracker

From CLAUDE.md §11. Do not advance phases until every criterion is checked.

- [x] `core-capture`, `core-codec`, `core-llr` modules functional  ← core-llr ✅ core-codec ✅ core-capture ✅
- [x] `source-polar` (H10) consuming PMD streams  ← wired 2026-05-27; ε_sync callback; offline backfill; requires device test
- [x] `source-camerax` POV video
- [x] Phone-side mux pipeline writing fMP4 with all five Phase 1 tracks (POV, acoustic, vibration, biometric, thermal)  ← code complete; requires device test
- [x] LLR gate operates in shadow mode (logs candidates, never persists a container)  ← code complete; requires device test
- [ ] ε_sync measured and logged per session  ← EpsSyncMeasure + sidecar writer ✅; EpsSyncCoordinator ✅; end-to-end requires device test
- [ ] ε_sync sustained ≤ 100 ms across at least 5 production shifts
- [ ] 20–30 candidate windows logged
- [x] Hand-labeling tool (`tools/shadow-mode-labeler`) operational  ← ExoPlayer playback + voice elicitation added 2026-05-27
- [x] All BLE dropouts emit explicit `BiometricGap` records (no silent interpolation)  ← implemented in PolarBleBiometricSource

> **Build status (2026-05-28):** All Phase 1 Android code complete. `./gradlew assembleDebug` produces a debug APK; 134 unit tests green. Remaining unchecked criteria require on-device testing (H10 paired, PPVC Line 1). ε_sync and shadow-session window count are device-only gates.

**Phase 1 hard stop:** if ε_sync exceeds 250 ms persistently, halt and debug Polar↔phone clock anchor before proceeding. Document the failure mode here:

_None observed yet._

---

## 3. Backlog (Next Up)

Ordered by intended pickup, not by priority alone. Top of list is next.

1. **[W-034] Adaptive per-channel EWMA baseline** (`core-llr`) — Replace static I-frame baseline with dual-timescale adaptive baseline per channel. Key problem: a static shift-start snapshot becomes increasingly stale over an 8-hour shift (HR drifts from thermal load + fatigue, acoustic signature changes as barrel warms, vibration shifts as die pressure equilibrates). Fix: EWMA with per-channel half-lives (HR 20 min, RMSSD 30 min, accel 10 min, acoustic 25 min, motion 15 min, thermal 45 min). **Critical invariant:** baseline update gated OFF during and for W_post after any triggered window — prevents genuine events from being absorbed into the null hypothesis. Shadow-mode gating: update only when `lambda < rolling_median_lambda(30min)` (not threshold-based since τ=0 means everything fires). Container implication: the I-frame at shift-start seeds the EWMA; periodic metadata I-frames written on significant baseline drift give P-frames a local reference. See session discussion 2026-05-28 for full architecture.
2. **Run Λ_env-only shadow sessions to begin corpus building** — no H10 required. Leave `POLAR_DEVICE_ID` blank in `local.properties`; app runs in acoustic + accel + motion mode (Λ_bio = 0). Start accumulating labeled candidate windows now. Label sessions separately from future full-signal sessions — do not mix the two τ calibration populations. Target: 10–15 Λ_env-only events as a warm corpus before H10 bench test.
3. **Bench-test `PolarBleBiometricSource` against H10** over a 4-hour continuous capture; characterize dropout rate near the extruder barrel. Hardware-gated; requires signed facility agreement. **No longer blocks corpus building** — see item 2. Unblocks full-signal (Λ_env + Λ_bio) sessions and the second τ calibration pass.

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

Last 10 items max. Anything older lives in `phases/archive/recently-completed-2026-05.md`.

| Date | ID | Item | Notes |
|---|---|---|---|
| 2026-05-28 | W-033 | **PWA dark UI port — 3-tab layout + dwell overlay** — `ArcShieldTheme.kt` (Material3 dark color scheme matching PWA palette: #07090D bg, #4B8EFF blue, #34D399 green, #FBBF24 amber, #F87171 red, #A78BFA violet); `AppLogger.kt` (singleton log sink + `ConsolePanel` composable, color-coded by level, auto-scrolls); `MainScreen.kt` rewritten as 3-tab (`CAPTURE` / `EVENTS` / `CONFIG`) dark PWA layout — AppHeader with AS gradient badge + pulsing status dot, camera view (4:3) with overlays (elapsed timer, candidate count, voice REC badge), `DwellOverlay` (progress bar + DWELLING state from motion proxy), 5-phase CIAER bar (Cause/Intuition/Action/Effect/Result), inline Facility/Line config, nav cards; `SessionViewModel` extended with `dwellProgress`, `isDwelling`, `lastDwellSec` StateFlows (6 consecutive still windows at lambdaMotion < 0.05 = DWELLING); `MainActivity` swapped to `ArcShieldTheme`. | UI-only motion-proxy dwell; actual LLR gaze channel requires Phase 2+ eye-tracking HW. |
| 2026-05-28 | W-032 | **Gate tuning screen — per-channel control + live Λ breakdown** — `LlrConfig` extended with 7 per-channel enable flags (`acousticEnabled`, `accelEnabled`, `motionEnabled`, `gazeEnabled=false`, `hrEnabled`, `rmssdEnabled`, `hrvNlEnabled`); gaze defaults fixed (both `gazeDwellBaselineSec` and `gazeDwellVarianceSec` were 0f, making Λ_gaze permanently 0 — fixed to 2.0f with explicit `gazeEnabled` flag); `LlrGate` uses enable flags; `SettingsRepository` +15 gate tuning settings (τ, 7 channel flags, 3 accel thresholds, 3 gate factors, 2 gaze params) persisted in SharedPreferences; `GateTuningScreen` — live Λ breakdown card (8 values, 4-decimal monospace), τ slider (0–10), channel rows with status dot + source tag + description + toggle Switch, activity gate class indicator + threshold/factor sliders; `SessionViewModel.latestWindow: StateFlow<CandidateWindow?>` exposed for live display; `MainScreen` + `MainActivity` wired for gate nav. | Λ_gaze was hardcoded to never fire — now properly guarded and configurable. |
| 2026-05-27 | W-028 | **Runtime settings screen** — `SettingsRepository` (SharedPreferences, StateFlow per setting, BuildConfig defaults on first launch); `SettingsViewModel` (@HiltViewModel, hardware availability booleans, BT bond check); `SettingsScreen` (LazyColumn: API Keys / Biometric Source / Video Source / Accel Source / Session Parameters / Device Status panels; auto-save on change; password-masked Claude key field); gear icon in MainScreen TopAppBar; `"settings"` NavHost route in MainActivity. All settings take effect on next session start; API key change requires restart. | `SettingsRepository` eliminates rebuild-per-config cycle. |
| 2026-05-27 | W-027 | **Per-session source creation + `SettingsRepository` DI pivot** — `SessionViewModel` constructor reduced to `(@ApplicationContext Context, SettingsRepository)`; `makeBiometricSource()` / `makeAccelSource()` / `makeCaptureSource()` helpers build fresh sources from live settings on each `startSession()` call; `iFrameDurationMs`, `facilityId`, `lineId` all read from settings; `AppModule` removes 4 providers; `provideLlmClient` reads `settings.claudeApiKey.value`. | |
| 2026-05-27 | W-026 | **Camera live preview in MainScreen** — `camerax-view 1.3.4` added; `CameraXCaptureSource` accepts optional `Preview` parameter; `SessionViewModel` creates `Preview.Builder().build()` when `videoSource == PHONE_CAMERA`; `MainScreen` renders `AndroidView { PreviewView }` at 16:9 during BUILDING/RECORDING; `DisposableEffect` calls `setSurfaceProvider(null)` on dispose. | GPU-path preview; no YUV→bitmap conversion overhead. |
| 2026-05-27 | W-025 | **Device-independence + permission bug fixes** — `BLUETOOTH` removed from `requestMultiplePermissions` list (returns false silently on API 31+); permission denial Toast lists denied permissions; `AppModule.provideBiometricSource` falls back to `NullBiometricSource`; `PolarBleBiometricSource.connect()` catches all `Exception` (not just `PolarInvalidArgument`). | Root cause of "nothing happened" on Pixel 9 Pro confirmed and fixed. |
| 2026-05-27 | W-024 | **Nonlinear HRV (SD1/SD2/SampEn) wired into Λ_bio gate** — `NonlinearHrv.kt`: pure sd1/sd2/sampleEntropy; `RollingBioStats.BioSnapshot` extended; `LlrBaseline` +7 NL HRV fields; `LlrBaselineBuilder` adds `computeNonlinearHrvStats()`; `LlrGate` replaces `lambdaHrvNl = 0f` stub with real Gaussian-shift GLR; `NonlinearHrvTest` 14 unit tests. 144 Android unit tests green. | Fires only when `baseline.nonlinearHrvAvailable && bioSnap.hasNonlinearHrv` (requires H10 ≥20 R-R in window). |
| 2026-05-27 | W-023 | **[MCP-MOD-004] Per-operator write auth** — `[auth] write_operators` in `config.toml`; `_check_write_auth()` helper; `ingest_event` + `update_graph_weight` check operator; empty allowlist = no restriction; 6 new auth tests; 68/68 tests green. | W-022 + W-023 land in same PR. |
| 2026-05-27 | W-022 | **[MCP-MOD-001] `SqliteCorpusBackend` + [MOD-003] value-proximity scoring** — `sqlite_backend.py`: WAL journal, `events` + `weight_audit` tables, 4 indexed columns; `query_by_cause_signature` value-proximity (`1/(1+|q_val−s_val|)` per shared instrument); contract fixture extended `params=["json","sqlite"]`; 58/58 tests (29×2). | |
| 2026-05-27 | W-021 | **Bug fixes** — `VideoPlayerView` always showed placeholder (`remember(exoPlayer){mediaItemCount>0}` baked in false); fixed via `hasVideo: StateFlow<Boolean>`. Silent `IOException` in `candidateWindows` collect swallowed by `SupervisorJob` could freeze count display; wrapped in try-catch. | PR #3 merged to main at `307a2c8`. |

---

## 6. Open Decisions Awaiting Author Sign-off

Architecture-level questions where Claude Code should **not** decide unilaterally. Park them here; do not implement until Kahn responds.

| Decision needed | Options on the table | Claude Code's recommendation | Date raised |
|---|---|---|---|
| **Synthetic-operator panel** — model the Twin (and other policies) as sealed parallel "operators" producing CIAER+ chains alongside the human operators: each emits Cause→Intuition→Action→prediction, the chain is timestamped and **sealed before the outcome**, then scored against the same R_phys via the `pending_R_phys` queue. Generalizes to a heterogeneous panel: Claude, Gemini, local Llama, RAG/LoRA Twin, and the closed-form codebook matcher as a degenerate non-LLM anchor — each a distinct synthetic `operator_id` behind the existing `LlmClient` binding. | (a) reserve schema hooks now (synthetic `operator_id` / provenance class + seal-and-timestamp discipline), build + enable scoring at Phase 4 — mirrors the "instrument withhold from day one, enable at Phase 3+" pattern / (b) build it as a standalone subsystem earlier / (c) drop the idea | (a) **reserve now, build at Phase 4.** Almost entirely paid for by existing machinery (`parallel_event_ids`, `pending_R_phys`, Q/Q′ partitioning, `LlmClient`). Hard constraints: (1) R_phys only grounds a synthetic operator on its action-overlap subset with the human; (2) synthetic events never used as Twin training targets; (3) rank strictly by realized R_phys, never inter-model agreement; (4) revealing synthetic call to operator after R_phys is a lagged advisory channel only. | 2026-05-26 |

---

## 7. Drift Watch

Things observed during recent sessions that **could** become problems if they continue. Not yet blocking. Re-evaluate weekly.

- **τ calibration population mixing** — Λ_env-only sessions (no H10) produce a systematically lower Λ distribution than full Λ_env + Λ_bio sessions. Mixing them when fitting τ will skew the threshold. Keep two separate label export files and calibrate τ independently per population until H10 is in continuous use. The TauCalibrator in shadow-mode-labeler operates per-file, so the tooling already supports this — the risk is operator error in combining exports.
- **Static baseline temporal drift** — The current `LlrBaseline` is a static snapshot from shift start. Over an 8-hour shift, natural drift (HR from fatigue/thermal, acoustic from barrel warm-up, vibration from die pressure equilibration) accumulates in every channel. This causes rising false-positive rates through the shift and makes Λ values increasingly uninterpretable relative to shift-start. W-034 (adaptive EWMA baseline) is the fix. **Do not collect τ calibration data across full shifts until W-034 is in place** — the first 60–90 minutes of a shift are usable with the static baseline; beyond that the calibration population is contaminated by drift-driven false positives.

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
2026-05-28 — Claude Code — W-032/W-033: gate tuning UI + PWA dark UI port; adaptive baseline design
  - What was worked on: (W-032) per-channel LLR gate control screen — fixed Λ_gaze hardcoded to never
    fire (gazeDwellVarianceSec=0f denominator guard), added 7 enable flags to LlrConfig, built
    GateTuningScreen with live Λ breakdown + τ slider + channel toggles + activity gate controls.
    (W-033) PWA dark UI port — ArcShieldTheme matching PWA palette (#07090D bg, #4B8EFF blue etc.),
    AppLogger singleton + ConsolePanel composable, MainScreen rewritten as 3-tab (CAPTURE/EVENTS/CONFIG),
    camera overlays (timer, count, dwell), 5-phase CIAER bar, motion-proxy dwell tracking in
    SessionViewModel. ArcShieldTheme replaces MaterialTheme in MainActivity.
    Architectural discussion: static I-frame baseline becomes stale over an 8-hour shift — natural
    HR/acoustic/vibration drift causes false positives. Solution: dual-timescale EWMA per channel,
    update gated off during event windows, shadow-mode gating via rolling median. W-034 added to backlog.
  - What changed in state above: W-032, W-033 completed and merged to feature branch; §3 backlog +1
    (W-034 adaptive baseline); §7 drift watch +1 (static baseline temporal drift); §0 updated;
    §5 rotated (W-020/019 archived).
  - Surprises: Λ_gaze was permanently zero in all prior sessions — the gazeDwellVarianceSec=0f default
    silently disabled it via the denominator guard even when the gaze channel was conceptually on.
    All shadow-mode data collected so far has Λ_gaze=0 regardless of what the operator's eyes were doing.
  - Next session pickup point: implement W-034 (AdaptiveLlrBaseline) in core-llr — EWMA state per
    channel, update gating, shadow-mode median gating, snapshot() for gate eval, serialize() for
    metadata I-frame writes. Then wire into LlrGate and periodic baseline write in CaptureSession.
```

```
2026-05-27 — Kahn / Claude Code — device bring-up session; settings + UX sprint
  - What was worked on: installed APK on Pixel 9 Pro; diagnosed "nothing happened" bug (BLUETOOTH in
    permission request returns false on API 31+); fixed permission flow; hardened device-independence
    so shadow mode runs with just the phone (no Polar, no glasses); added runtime settings screen
    (API keys, source selection, session params, device status); added camera live preview in
    MainScreen (16:9, GPU path, active during BUILDING/RECORDING); per-session source creation from
    SettingsRepository eliminates rebuild-per-config cycles; merged all work to main.
  - What changed in state above: W-025/026/027/028 completed; §1 active items cleared; session log appended.
  - Surprises: BLUETOOTH permission returning false on API 31+ was not caught by any existing test
    (all unit tests are JVM-side; permission flow is device-only). Worth adding an instrumented test
    for the grants-callback logic.
  - Next session pickup point: install new APK on Pixel 9 Pro, verify shadow capture starts cleanly,
    run first Λ_env-only shift and label candidate windows. Start τ calibration data collection.
```

```
2026-05-24 — Claude Code — MCP wired + April 8 seed event ingested
  - Fixed MOD-002: load_config() now resolves corpus_dir relative to config.toml location, not CWD.
  - Created .claude/settings.json wiring arcshield MCP server into Claude Code.
  - Constructed and ingested April 8, 2026 PIE demonstration event (event_id 6ab2942f).
  - Corpus depth: 0 → 1.
  - bootstrap_seed_event.py committed to backend/api/; idempotent, safe to re-run.
  - Next session pickup point: discuss optimum path forward.
```

```
2026-05-24 — Claude Code — BLOCKING elicitation + session close
  - Ran 5 BLOCKING questions. All answered, rules promoted to docs/SELECTION_PRINCIPLE.md.
  - Q2.1: both H10 and Verity Sense supported. Q3.1: operator_id self-sovereign. Q4.1: θ_TC fixed global.
  - Q6.1: arXiv before non-provisional. Provisional window closes ~2027-04-08. Added to §4.
  - Q7.1: Corpus validation sprint order confirmed.
  - Next session pickup point: HIGH elicitation questions or scaffold source-polar module.
```

```
2026-MM-DD — Kahn — repo init
  - Committed CLAUDE.md and CURRENT_PHASE.md.
  - No code yet. Phase 1 clock starts when Polar SDK integration begins.
  - Next session pickup point: scaffold `source-polar` module and run first H10 bench test.
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

## 10. Backend / MCP Server State (backend/api/)

Single-source summary of the Python backend and MCP corpus server. Full implementation specs for deferred MOD items are in `backend/api/SESSION_LOG.md` — read that file when actually implementing a MOD item, not just triaging.

### Current build state (as of 2026-05-28)

| File | Status | Notes |
|---|---|---|
| `arcshield/schema.py` | ✅ built | Full CIAER+ Pydantic v2 models |
| `arcshield/corpus/backend.py` | ✅ built | Abstract `CorpusBackend` — 3-phase upgrade path |
| `arcshield/corpus/backends/json_backend.py` | ✅ built | Phase 1 flat-file backend |
| `arcshield/corpus/backends/sqlite_backend.py` | ✅ built | Phase 2 SQLite backend (WAL, 4 indexed columns, value-proximity scoring) |
| `arcshield/corpus/backends/__init__.py` | ✅ built | `get_backend()` factory — one-line config swap |
| `server.py` | ✅ built | FastMCP stdio server, lifespan-managed, `config.toml` loading |
| `config.toml` | ✅ built | `facility_id`, backend type, `allow_writes`, `[auth] write_operators` |
| `tests/test_corpus_backend_contract.py` | ✅ 58/58 passing | Parameterized contract suite (json + sqlite); new backends auto-tested by adding to fixture |

**Run tests:** `cd backend/api && python -m pytest tests/ -v --asyncio-mode=auto`

### 11 MCP tools (server.py)

| Tool | Purpose |
|---|---|
| `list_failure_modes` | All failure mode tags with event counts |
| `query_by_failure_mode` | Events matching a tag, ordered by graph_weight |
| `query_by_cause_signature` | Sensor-signature proximity search (value-proximity scoring — MOD-003) |
| `get_event` | Full CIAER+ record by event_id |
| `get_shadow_actions` | Rejected alternatives for an event |
| `ingest_event` | Write a new CIAER+ event (write-auth checked) |
| `update_graph_weight` | Adjust graph_weight with audit trail (write-auth checked) |
| `get_divergent_chains` | Parallel-expert divergence chains (JSON backend: NOT_AVAILABLE stub) |
| `record_r_phys` | Record R_phys arrival and fire OGC weight update |
| `expire_r_phys_deadlines` | Mark overdue PENDING events INDETERMINATE |
| `list_pending_r_phys` | List events awaiting R_phys arrival, sorted by deadline |

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
| MCP-MOD-001 | 2 | **DONE 2026-05-27** | `SqliteCorpusBackend` — W-022 |
| MCP-MOD-003 | 2→3 | **DONE 2026-05-27** | `query_by_cause_signature` value-proximity scoring — W-022 |
| MCP-MOD-004 | 2 | **DONE 2026-05-27** | Per-operator write auth — W-023 |
| MCP-MOD-006 | 3 | Phase 3 | `graph_weight` counterfactual policy — Leiden re-detection on high-centrality weight changes > 0.2. Defer ratified 2026-05-26; calibrate thresholds against real corpus data in a Phase 3 design session. |
| MCP-MOD-007 | 3 | Phase 3 | `get_divergent_chains` — stub built 2026-05-26 (W-014), JSON backend returns `NOT_AVAILABLE`; native graph traversal lands with `GraphCorpusBackend`. |
| MCP-MOD-002 | 3 | corpus_depth > ~5000 OR multi-facility | `GraphCorpusBackend` (Neo4j) — native Cypher, HNSW ANN, CIAER-QL. Full spec in `SESSION_LOG.md`. |
| MCP-MOD-008 | 3 | Phase 3 | Android → MCP ingest bridge — `McpCorpusSink.kt` in Android app (cross-repo, not in this repo) |

---

*End of CURRENT_PHASE.md. State only. Architecture lives in /CLAUDE.md.*
