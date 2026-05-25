# ArcShield / mp4Real™ — Brainstorming Handoff
**Date:** 2026-05-25 | **Phase:** 1 of 5 — Week 1 of 6 | **Build:** 🟢 healthy

---

## Who and What

**Author / Architect:** Kahn Capps, Capps Consulting Company LLC (Helena-West Helena, AR)
**Validation site:** PPVC Line 1, Hollowell Industries — active PVC extrusion line
**Key fact:** The expert being modeled is the same person building the system. Kahn is both the operator whose tacit knowledge is being captured and the architect of the capture apparatus.

**IP:** mp4Real™, ArcShield™, CIAER™, CIAER+™ — trademarks of Capps Consulting Company LLC. Provisional patent filed; priority date 2026-04-08. Non-provisional window closes ~2027-04-08.

---

## The Central Insight (Read This First)

ArcShield is not a data logger. It is a **domain-specific cyber-physical codec** — mathematically identical in structure to a video codec — operating over the Minimum Description Length (MDL) principle on multimodal industrial decision events.

> The edge device is a **rate-distortion gate**, not a recorder. It drops the overwhelming majority of what it sees. The backend is a **demuxer and codebook synchronizer**, not a database. Storing raw waveform is the *fallback* path — only when no codebook primitive matches.

Three architectural commitments that cannot be retrofitted:

**C1 — Codec gating before persistence.** All sensor channels enter a ring buffer. The LLR gate computes Λ = Λ_env + Λ_bio vs. shift-start baselines. Only when Λ ≥ τ does the window [t − W_pre, t + W_post] get muxed into the mp4Real container. Default: drop.

**C2 — Outcome-Grounded Confidence (OGC).** Confidence updates are foreign-key-constrained on physical telemetry R_phys. Operator compliance [a_t = â_t] is a *gating scalar*, never a reward source. This is the anti-reflexivity firewall, enforced at the database schema level.

**C3 — Withhold-sampling as first-class subsystem.** Ships Phase 1–2 with p_withhold = 0. Activates at 0.05 in Phase 3. Counterfactual distribution Q′ is stored separately from Q from day one.

---

## The CIAER+ Schema — Atomic Data Unit

One JSON object per complete expert decision cycle. Every field defeats a specific failure mode in learning from human expertise.

```
{
  schema_version, event_id,
  envelope:       { operator_id, facility_id, line_id, parent_event_id, parallel_event_ids }
  pre_env:        { shift_phase, material_batch_id, ambient_temp_f, crew_state_tag }   ← I-frame context
  cause:          { trigger_source, sensor_readings[], biometric_snapshot, acoustic_profile, gaze_dwell }
  intuition:      { srk_level, causal_hypothesis, failure_mode_tag, confidence_level, projection, voice_transcript }
  action:         { action_type, action_sequence[{step_id, param, from, to, rationale}] }
  shadow_actions: [ { action_type, rejection_rationale, confidence_in_rejection } ]   ← first-class, not a note
  effect:         { sensor_readings[], deltas[{delta, IMPROVED|DEGRADED|UNCHANGED}], prediction_match }
  result:         { outcome_tag, hypothesis_confirmed, model_revision, graph_weight }
}
```

Key schema invariants (enforced at write time):
- `prediction_match = INDETERMINATE` requires a `pending_R_phys` deadline — or the record is rejected
- `shadow_actions` is REQUIRED at KNOWLEDGE-level SRK; optional at SKILL-level
- `model_revision` is REQUIRED when `hypothesis_confirmed = false`

---

## The mp4Real™ Container — Format

ISO BMFF (fragmented MP4) with 7 timed tracks:

| Track | Content | Format | Phase |
|---|---|---|---|
| 1 | POV video | H.265 / HEVC, 30fps 1080p | 1 |
| 2 | Acoustic | AAC, 48kHz mono | 1 |
| 3 | Vibration/accelerometer | octet-stream timed metadata | 1 |
| 4 | Biometric (Polar H10/Verity Sense) | octet-stream timed metadata | 1 |
| 5 | Thermal | octet-stream timed metadata | 1 |
| 6 | Voice annotation | octet-stream timed metadata | 1 |
| 7 | PLC telemetry | octet-stream timed metadata | Gen 2+ |

**ε_sync:** All tracks anchored to `elapsedRealtimeNanos` (phone monotonic clock). Polar PMD anchored at first packet receipt + sample index arithmetic. Target ≤ 100ms. > 250ms → `low_sync_confidence` flag. Written to `.mp4real.json` sidecar on container close.

**I-frame:** 60–120s all-channel baseline at shift start. PTS = 0. Parameterizes LLR null hypothesis H₀.
**P-frame:** [t − W_pre, t + W_post] window on gate fire. Default W_pre = 30s, W_post = 60s.

---

## The LLR Gate

Closed-form streaming statistics in Phase 1 (no learned model — you can't train it without a corpus, and you can't get a corpus until the gate works).

**Λ_env** = Λ_acoustic (KL divergence of rolling vs. baseline power spectrum) + Λ_accel (Gaussian-shift GLR on rolling 5s RMS) + Λ_motion (frame-to-frame Y-plane MAD GLR) + Λ_gaze (sustained dwell — stub, Phase 1)

**Λ_bio** = (Λ_hr + Λ_rmssd + Λ_hrv_nl) × activity_gate
- Activity gate: high physical exertion suppresses bio signal (resting=1.0 / light=0.8 / moderate=0.4 / vigorous=0.1)
- Λ_hrv_nl (SD1/SD2, sample entropy) — stubbed 0.0 until H10 ECG path live (Phase 2)

**Λ = Λ_env + Λ_bio** — valid under S_env ⊥ S_bio | E, Z (conditional independence; Kay 1998 GLR)

Shadow mode: gate logs every candidate window, never persists a container. Hand-label at shift end → calibrate τ for ~80% TP / ≤20% FP → lock τ → begin live capture.

---

## The Behavioral Codebook

Phase 1: Python dict keyed by `failure_mode_tag`, cosine similarity on per-track embeddings. Debuggable. First tag: `material_segregation_funnel_flow`.

Phase 3+: Residual VQ (3 stages × 256 codes) trained on per-track causal Transformer encoders via VICReg contrastive loss. CIAER+ DAG is a structural overlay on each codebook entry, not part of the quantizer.

**Match logic (FIG. 8):** For each captured window, compute TC(T₁…Tₖ | πᵢ) for all primitives. If max TC ≥ θ_TC: emit compressed primitive-reference token. If TC < θ_TC: store full waveform, queue HITL validation. Validated novel events expand the codebook.

---

## Outcome-Grounded Confidence (OGC)

```
δ = R_phys(T_{t+Δt}) − C_t(â_t | s_t)
C_{t+1} = C_t + α · δ · [a_t = â_t]
```

- R_phys: scalar from PLC telemetry (or optical vision telemetry — see below) measuring physical state vs. spec
- [a_t = â_t]: compliance gate. Lives in a separate column from the reward. FK-constrained to pending_R_phys
- Deferred queue: keyed by (event_id, deadline). Timeout → INDETERMINATE, C not updated

**The firewall:** No code path may let operator compliance be evidence of physical success. The two signals read from separate tables.

---

## Biometric Integration — Polar PMD

Switched from Pixel Watch 4 / Health Connect to Polar H10 (ECG 130Hz) and Verity Sense (optical PPG). PMD protocol gives raw R-R intervals via BLE. Clock-anchored at first packet receipt, advanced by sample index.

BLE in industrial environment: explicit `BiometricGap(start, end, reason)` events emitted on dropout — never silently interpolated. Gaps ≥ 4s set `low_sync_confidence`. H10 holds ~30 min offline; backfill sync on reconnect (stubbed for Phase 2 implementation).

---

## Architectural Pivot — VisionTelemetrySource (W-008)

The Hollowell PLC API surface is not yet mapped. Rather than block R_phys extraction, a `VisionTelemetrySource` is planned that conforms to the `PlcTelemetrySource` interface. It uses the CameraX POV frame stream + LLM vision inference to optically read process gauges and displays. Key math: optical motor RPM readings must be divided by the 20:1 gearbox ratio to yield actual screw RPM for the corpus.

This preserves the interface contract — when the PLC API is eventually mapped, it's a DI binding swap, not a rewrite.

---

## Provider Abstraction Interfaces (Kotlin)

All hardware consumed through interfaces. Gen 1 → Gen 2 swap (phone → Meta Ray-Ban + PLC) is a DI binding change, not a structural rewrite.

```kotlin
interface CaptureSource    { fun videoFrames(): Flow<VideoFrame>; fun audioFrames(): Flow<AudioFrame>; val sourceId: String }
interface BiometricSource  { fun heartRate(): Flow<HrSample>; fun rrIntervals(): Flow<RrSample>; fun ecgWaveform(): Flow<EcgSample>; fun accelerometer(): Flow<AccelSample>; fun edaWaveform(): Flow<EdaSample>; fun gaps(): Flow<BiometricGap>; val capabilities: Set<BiometricChannel>; val sourceId: String }
interface PreEnvSource     { suspend fun captureBaseline(): PreEnvSnapshot; fun ambientTemp(): Flow<TempSample> }
interface CorpusSink       { suspend fun persistEvent(event: CiaerPlusEvent): Result<EventId>; suspend fun persistContainer(uri: Uri, eventId: EventId): Result<Unit>; suspend fun queuePendingRPhys(eventId: EventId, deadline: Instant) }
interface LlmClient        { suspend fun elicit(prompt: ElicitationPrompt, context: SensoryContext): ElicitationResponse; suspend fun generateGuidance(query: GuidanceQuery): TwinGuidance; val providerId: String }
interface PlcTelemetrySource { fun channel(channelId: String): Flow<TelemetrySample>; suspend fun snapshotAt(t: Instant): TelemetrySnapshot }
```

---

## Repository Structure — Current State

```
Mp4Real/
├── CLAUDE.md                          ← architecture genome, never drifts
├── ARCHITECTURE_PRINCIPLES.md         ← von Neumann triple / fractal pattern doc
├── README.md
├── phases/
│   └── CURRENT_PHASE.md               ← live build state
├── android/
│   ├── settings.gradle.kts            ← all modules declared
│   ├── gradle/libs.versions.toml      ← version catalog
│   ├── core-schema/                   ✅ BUILT
│   │   └── biometric/BiometricSource.kt    (interfaces + all sample types)
│   │   └── capture/CaptureSource.kt        (VideoFrame, AudioFrame, CaptureSource)
│   ├── core-llr/                      ✅ BUILT — 6 test classes, 40+ tests
│   │   ├── LlrBaseline.kt             (I-frame statistical snapshot)
│   │   ├── LlrBaselineBuilder.kt      (90s accumulator: Welford spectral + accel + RMSSD)
│   │   ├── LlrConfig.kt              (τ, window sizes, activity gate thresholds)
│   │   ├── LlrGate.kt                (channelFlow: audio+accel+HR+RR+video producers + eval ticker)
│   │   ├── CandidateWindow.kt        (@Serializable, all Λ components + activityGate)
│   │   └── internal/
│   │       ├── RealFft.kt            (Cooley-Tukey radix-2 DIT, pure Kotlin)
│   │       ├── KlDivergence.kt       (ε-smoothed KL divergence)
│   │       ├── RollingStats.kt       (Welford online variance, circular buffer)
│   │       ├── RollingAccelRms.kt    (accel magnitude rolling RMS)
│   │       ├── RollingBioStats.kt    (5-min rolling HR + RMSSD, activity gate)
│   │       └── FrameDiffMotion.kt    (Y-plane MAD, subsample=4, unsigned byte safe)
│   ├── core-codec/                    ✅ BUILT — 2 test classes, 23 tests
│   │   ├── Mp4RealConfig.kt          (W_pre=30s, W_post=60s, iFrameDuration=90s)
│   │   ├── TrackType.kt              (sealed: Video/Audio/AccelMeta/BiometricMeta/ThermalMeta/VoiceMeta/PlcTelemetryMeta)
│   │   ├── EncodedSample.kt          (timestamped ByteArray, nanos throughout)
│   │   ├── MetaSample.kt             (timed metadata payload, JSON-UTF-8 Phase 1)
│   │   ├── EpsSyncMeasure.kt         (measure + combine; 100ms target / 250ms threshold)
│   │   ├── SessionMetadata.kt        (@Serializable + writeSidecar → .mp4real.json)
│   │   ├── Mp4RealMuxer.kt           (interface — JVM-testable without Android stubs)
│   │   ├── AndroidMp4RealMuxer.kt    (MediaMuxer-backed; MPEG-4; text/vtt metadata; API 26+ safe)
│   │   └── Mp4RealWriter.kt          (orchestrator: register tracks → start → write → close)
│   ├── source-polar/                  ✅ BUILT (no unit tests — hardware-dependent)
│   │   ├── PolarDeviceType.kt        (H10 / VERITY_SENSE; capabilities, supportsOfflineRecording)
│   │   └── PolarBleBiometricSource.kt (PMD clock anchor, gap emission, reconnect backoff, backfill stub)
│   ├── source-camerax/                ✅ BUILT (no unit tests — Android instrumented)
│   │   └── CameraXCaptureSource.kt   (ImageAnalysis NV21, AudioRecord 48kHz, elapsedRealtimeNanos)
│   ├── shadow-mode-labeler/           ✅ BUILT — 2 test classes, 13 tests
│   │   ├── CandidateWindowLog.kt     (NDJSON appender/reader, mutex-safe)
│   │   ├── NonMaxSuppressor.kt       (O(n log n) bucket-max, 57600→≤480 events/shift)
│   │   ├── TauCalibrator.kt          (suggests τ for ≥80% TP / ≤20% FP)
│   │   ├── LabelerViewModel.kt       (AndroidViewModel, shift list, label state, export)
│   │   └── ui/                       (LabelerScreen, CandidateWindowRow, TauSummaryCard)
│   ├── core-capture/                  🔲 EMPTY — next major build target
│   ├── app/                           🔲 EMPTY — Compose entry point + DI
│   ├── source-emotibit/               🔲 stub (EDA — deferred, no Polar support)
│   ├── source-meta-raybans/           🔲 stub (Gen 2 capture — future)
│   ├── source-openmeteo/              🔲 stub (ambient temp proxy)
│   ├── source-plc/                    🔲 stub (PLC API — Gen 2+)
│   ├── llm-claude/                    🔲 stub
│   ├── llm-gemini/                    🔲 stub
│   └── debrief-ui/                    🔲 stub (HITL validation screen — Phase 2)
├── backend/
│   └── api/                           ✅ BUILT — MCP server, 28/28 contract tests passing
│       ├── server.py                  (FastMCP stdio, 7 tools)
│       ├── config.toml                (facility_id, backend type, allow_writes)
│       ├── arcshield/
│       │   ├── schema.py              (full CIAER+ Pydantic v2 models)
│       │   └── corpus/
│       │       ├── backend.py         (CorpusBackend ABC — 3-phase upgrade path)
│       │       └── backends/
│       │           └── json_backend.py (Phase 1 flat JSON; 28/28 contract tests)
│       ├── corpus/events/
│       │   └── 6ab2942f-...json       (April 8, 2026 PIE demo seed event)
│       └── tests/
│           └── test_corpus_backend_contract.py (parameterized; new backends auto-tested)
│   ├── codebook/                      🔲 empty
│   ├── graph/                         🔲 empty (Kuzu Phase 2, Memgraph Phase 4+)
│   ├── ingest/                        🔲 empty
│   ├── ogc/                           🔲 empty (R_phys deferred queue + C updater)
│   ├── twin/                          🔲 empty (RAG Phase 2, LoRA Phase 4)
│   └── withhold/                      🔲 empty (Q/Q′ partitioning, D_KL monitor)
├── ciaer-ql/                          🔲 empty (Cypher-compatible probabilistic query layer)
├── ml/
│   ├── augmentation/                  🔲 empty (Monte Carlo — structured fields only, never voice)
│   ├── embedding/                     🔲 empty (per-track encoders, Phase 3)
│   ├── rvq/                           🔲 empty (residual VQ codebook, Phase 3+)
│   └── lora/                          🔲 empty (Twin adapter training, Phase 4)
├── docs/
│   ├── ELICITATION_LOG.md             (answered/open design questions)
│   ├── SELECTION_PRINCIPLE.md         (promoted architectural decisions)
│   ├── patent/ArcShield_Patent_Drawings_Figs6-10.pdf
│   └── whitepaper/ArcShield_Whitepaper_v10_3.pdf + others
└── tools/
    └── shadow-mode-labeler/README.md
```

---

## Corpus State

| Field | Value |
|---|---|
| Depth | 1 validated CIAER+ event |
| Event | `6ab2942f` — April 8, 2026 live PIE demonstration at PPVC Line 1 |
| failure_mode_tag | `material_segregation_funnel_flow` |
| srk_level | KNOWLEDGE |
| shadow_actions | 2 (first-class structured alternatives) |
| graph_weight | 0.88 |
| Codebook primitives | 0 |

---

## MCP Corpus Server — 7 Tools Live

The backend MCP server is wired into Claude Code via `.claude/settings.json`.

| Tool | Purpose |
|---|---|
| `list_failure_modes` | All tags + event counts |
| `query_by_failure_mode` | Events by tag, ordered by graph_weight |
| `query_by_cause_signature` | Sensor-similarity search (Phase 1: coverage score) |
| `get_event` | Full CIAER+ record by event_id |
| `get_shadow_actions` | Rejected alternatives for an event |
| `ingest_event` | Write new CIAER+ event (allow_writes = true) |
| `update_graph_weight` | Adjust weight with audit trail |

Run tests: `cd backend/api && python -m pytest tests/ -v --asyncio-mode=auto`

---

## What Is Done

| Work Item | Module | What Was Built |
|---|---|---|
| W-000 | repo | Full directory scaffold per CLAUDE.md §10 |
| W-001 | source-polar | BiometricSource interface; PolarBleBiometricSource (PMD clock anchor, gap emission, reconnect backoff 2s→30s, H10 backfill stub) |
| W-002 | core-llr | LlrBaseline, LlrConfig, CandidateWindow, llrGate(); RealFft (Cooley-Tukey), KlDivergence, RollingAccelRms; Λ_acoustic + Λ_accel live; shadow mode default |
| W-003 | core-llr | Λ_bio: RollingBioStats, HR delta GLR, RMSSD GLR, activity gate; 10 tests |
| W-004 | core-llr | LlrBaselineBuilder: 90s I-frame accumulator; SpectralAccumulator (Welford per-bin); RMSSD sub-window variance; injectable clock for JVM tests; 13 tests |
| W-005 | source-camerax + core-llr | CameraXCaptureSource (NV21 ImageAnalysis + AudioRecord); FrameDiffMotion (Y-plane MAD); Λ_motion wired in LlrGate; 8 tests |
| W-006 | shadow-mode-labeler | CandidateWindowLog (NDJSON), NonMaxSuppressor (O(n log n) bucket-max), TauCalibrator (80% TP / 20% FP), full Compose labeler UI; 13 tests |
| W-007 | core-codec | Mp4RealMuxer interface + AndroidMp4RealMuxer (MediaMuxer, text/vtt metadata, API 26+ safe) + Mp4RealWriter (orchestrator) + EpsSyncMeasure + SessionMetadata sidecar; 23 tests |
| — | backend/api | Full CIAER+ Pydantic schema; CorpusBackend ABC; JsonCorpusBackend; 7-tool MCP server; 28/28 contract tests; April 8 PIE demo seed event ingested |

---

## What Is Pending (Ordered)

1. **W-008 — `source-vision-telemetry`** *(architectural decision made, not yet built)*
   Implements `VisionTelemetrySource` conforming to `PlcTelemetrySource` interface. Uses CameraX + LLM vision to optically read process gauges when gaze_dwell triggers. Math: motor RPM optical reading ÷ 20:1 gearbox ratio = screw RPM. This unblocks R_phys extraction without the PLC API.

2. **`core-capture`** *(next critical path build)*
   Ring buffer (circular, all channels), MediaCodec H.265/AAC encoding, mux pipeline connecting LLR gate fire → Mp4RealWriter, ε_sync NTP-style handshake with Polar, CaptureSession lifecycle.

3. **`:app` module** *(Compose entry point + Hilt DI wiring)*
   Wires all sources → core-capture → core-codec. Hosts labeler screen in debug build. Manages shift lifecycle (I-frame → shadow mode → live capture).

4. **Bench-test `PolarBleBiometricSource` against H10** *(requires physical hardware)*
   4-hour continuous capture; characterize BLE dropout rate near 480V motors and induction heaters at PPVC Line 1. Gate the ε_sync acceptance criterion against this data.

5. **`backend/ingest`** — container upload + demux service
6. **`backend/codebook`** — Phase 1 dict matcher (cosine similarity on embeddings)
7. **`backend/graph`** — Kuzu embedded graph (Phase 2)
8. **`backend/ogc`** — R_phys deferred queue + confidence updater
9. **`backend/withhold`** — Q/Q′ partition, D_KL monitor
10. **`debrief-ui`** — HITL validation screen (Phase 2)
11. **`MCP-MOD-001`** — SqliteCorpusBackend (trigger: corpus > ~500 events or latency > 200ms)

---

## What Is Blocked

| Item | Blocked On | Unblock Condition |
|---|---|---|
| Non-provisional patent filing | Patent counsel + budget | File before 2027-04-08 (12-month provisional window). arXiv submission precedes non-provisional. **Hardest external clock in the project.** |
| Facility deployment agreement with Hollowell | Legal sign-off | Signed MSA + data-rights addendum. Required before any live line capture. |
| ε_sync acceptance criterion | Physical deployment + 5 shifts | Can only be measured at PPVC Line 1 with real Polar + phone under line conditions. |
| H10 ECG / nonlinear HRV (Λ_hrv_nl) | Physical device + bench test | Currently stubbed 0f. Requires H10 connected and ECG path live. Phase 2 item. |

---

## Phase 1 Acceptance Criteria Status

- ☐ `core-capture`, `core-codec`, `core-llr` modules functional — *core-llr ✅ core-codec ✅ core-capture pending*
- ☐ `source-polar` (H10) consuming PMD streams — *code built, bench test pending*
- ☑ `source-camerax` POV video
- ☐ Phone-side mux pipeline writing fMP4 with all five Phase 1 tracks
- ☐ LLR gate operates in shadow mode (logs candidates, never persists a container)
- ☐ ε_sync measured and logged per session — *EpsSyncMeasure + sidecar writer built; wiring pending (core-capture)*
- ☐ ε_sync sustained ≤ 100ms across at least 5 production shifts — *requires deployment*
- ☐ 20–30 candidate windows logged — *requires deployment*
- ☑ Hand-labeling tool (`shadow-mode-labeler`) operational
- ☐ All BLE dropouts emit explicit `BiometricGap` records — *code built, bench test pending*

---

## Phase Roadmap

| Phase | Timeframe | Key Deliverables | Gate |
|---|---|---|---|
| 1 — Container + LLR gate, shadow mode | Weeks 1–6 | core-capture, mux pipeline, shadow mode calibration | ε_sync ≤ 100ms × 5 shifts; 20–30 labeled candidate windows |
| 2 — Backend + manual codebook | Weeks 6–12 | ingest, hand-curated codebook dict, Kuzu graph, OGC schema, debrief-ui | 50+ validated CIAER+ events; disconfirmation path tested; OGC adversarial test passes |
| 3 — PLC/vision telemetry + embedding match + RAG Twin | Months 3–6 | source-vision-telemetry live, embedding-based codebook matcher, RAG Twin advisory, withhold coordinator (p_withhold=0) | R_phys auto-computed; Twin surfaces top-3 similar events; 150+ events |
| 4 — RVQ codebook + LoRA Twin + active withhold | Months 6–12 | RVQ codebook, LoRA adapter, p_withhold=0.05, Q/Q′ dashboard | 200+ events; Twin measurable accuracy; one withhold cycle completed |
| 5 — Multi-facility + Twin marketplace | Months 12+ | Out of scope for this handoff | — |

---

## Open Architectural Decisions (§6 — Author Sign-off Required)

| Decision | Options | Recommendation |
|---|---|---|
| `sensor_readings` to `query_by_cause_signature` as JSON string vs. structured param | (a) keep JSON string / (b) prompt template wrapper / (c) wait for MCP structured params | (a) for now |
| `get_divergent_chains` as 8th MCP tool stub now vs. wait for GraphCorpusBackend | (a) add stub now / (b) wait | (a) — zero cost, surface early |
| `graph_weight` Leiden re-detection threshold when high-centrality event changes > 0.2 | (a) 0.2 + top-10% betweenness / (b) different threshold / (c) defer Phase 4 | Defer to Phase 3 — need corpus data |
| BiometricSource when H10 + Verity Sense both paired | (a) prefer H10 for ECG / (b) prefer Verity Sense / (c) require explicit selection | (a) — mirrors CLAUDE.md §8.3 |

---

## Known Risks (Open Questions)

1. **HRV signal reliability in industrial environments** — Can RMSSD reliably distinguish cognitive decision events from physical exertion and thermal exposure? Empirical calibration sprint at PPVC Line 1 with H10 will answer this.
2. **LoRA corpus size threshold** — Preliminary estimate 200–500 events for a meaningful Personal AI Twin. Unknown until tested.
3. **Cortex aggregation privacy** — Federated learning vs. differential privacy not specified. Specialist ML work outstanding.
4. **Gaze dwell false-positive rate** — Head movement vs. sustained attention. Needs accelerometer gating. Ongoing.
5. **VisionTelemetrySource accuracy** — LLM optical gauge reading on a moving production line. No empirical data yet.
6. **corpus/events/ gitignore decision** — Seed event is committed (prior art provenance). Live line captures probably should be gitignored. Decide before first capture session.

---

## Things That Must Never Happen (Hard Constraints)

1. **Don't make the edge app a recorder.** Default is drop. Capture is the exception.
2. **Don't update confidence C without R_phys.** FK constraint at schema level, not a code convention.
3. **Don't silently interpolate across BLE dropouts.** Emit explicit BiometricGap.
4. **Don't string-match `failure_mode_tag` from free text.** Controlled vocabulary only. New tags require domain ontology registration.
5. **Don't conflate Q and Q′.** Separate tables from creation. Never merge.
6. **Don't ship a learned LLR gate in Phase 1.** Closed-form statistics only.
7. **Don't use Health Connect.** Dead. Polar PMD path replaced it.
8. **Don't augment voice transcripts.** Monte Carlo augmentation operates on structured sensor fields only.
9. **Don't write compliance [a_t = â_t] into the reward column.** It belongs in the gating column.

---

*Intellectual Property and Trademark Notice: mp4Real™, ArcShield™, CIAER™, and CIAER+™ are trademarks of Capps Consulting Company LLC. The multi-track cyber-physical capture architecture, the application of log-likelihood ratio (LLR) gating to multimodal industrial decision events, and the behavioral codebook discretization methods described in this document are the proprietary intellectual property of Kahn Capps and Capps Consulting Company LLC. Unauthorized commercial use, reproduction, or implementation without explicit licensing is prohibited. All rights reserved.*
