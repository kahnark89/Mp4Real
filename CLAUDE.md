# CLAUDE.md — ArcShield / mp4Real™ Build Handoff

**This file is the single point of context for any Claude Code instance working in this repository. Read it in full before making any code change, before reading any other file, and before answering any question about the codebase. Re-read the relevant section whenever you re-enter a long session.**

---

## 0. Identity Block

| | |
|---|---|
| **Project** | ArcShield — operational name for the mp4Real™ reality codec |
| **Author / Architect** | Kahn Capps, Capps Consulting Company LLC (Helena-West Helena, Arkansas) |
| **Validation site** | PPVC Line 1, Hollowell Industries (PVC extrusion) |
| **Primary author role** | Active PVC extrusion operator at Hollowell **and** architect of the capture system. The expert being modeled is the same person building the model. |
| **Current spec versions** | Whitepaper v10.3 / context handoff v10.4 / Patent Drawings Figs 1–10 / Schema Ref v1.0 |
| **Prior art priority date** | April 8, 2026 (live PIE demonstration at PPVC Line 1) |
| **Provisional patent** | Filed (micro entity status). arXiv submission of v10.x is gated on Paris Convention foreign-filing clock. |
| **IP marks** | mp4Real™, ArcShield™, CIAER™, CIAER+™, PIE — trademarks of Capps Consulting Company LLC |

### Reference documents already in the project knowledge

Read these in order **only if** the current task needs deeper context than this CLAUDE.md provides. This file should suffice for most build work.

1. `ArcShield_Whitepaper_v10_3.pdf` — canonical academic spec
2. `ArcShield Context Handoff v10.pdf` — v10.4 build-side handoff with mp4Real codec framing
3. `ArcShield InformationTheoretic Handoff.pdf` — MDL / codec isomorphism
4. `ArcShield_Patent_Drawings_Figs610.pdf` — **Figs 6–10 are architectural ground truth**. If implementation diverges from a figure, the figure wins.
5. `arcshield_schema_ref.docx` — CIAER+ field-by-field reference
6. `arcshield_product_architecture.docx` — four-layer intelligence architecture (Twin → Craft → Org → Super-Corpus)
7. `arcshield_marketplace_addendum.docx` — Twin marketplace and certification

---

## 1. The Central Insight That Determines Every Architecture Decision

ArcShield **is not a data logger with a knowledge graph attached.** ArcShield is a domain-specific cyber-physical codec — structurally and mathematically identical to a video codec — operating over the Minimum Description Length principle on multimodal industrial decision events.

If you internalize only one thing from this document, internalize this:

> The edge device is a **rate-distortion gate**, not a recorder. It is supposed to drop the overwhelming majority of what it sees. The backend is a **demuxer and codebook synchronizer**, not a database. Storage of raw multi-track waveform is the *fallback* path, emitted only when no primitive in the codebook matches the captured window.

Every implementation decision in this repo follows from that identity. If you ever find yourself writing code that captures and stores by default, **stop and re-read this section.** The default is discard.

### The three load-bearing architectural commitments

These three commitments cannot be retrofitted later. Get them right from event #1 or the corpus is contaminated.

**(C1) Codec gating before persistence.** Continuous streams from all sensor channels enter a ring buffer. The LLR gate computes Λ = Λ_env + Λ_bio against shift-start baselines. Only when Λ ≥ τ does the window [t − W_pre, t + W_post] get muxed into the mp4Real container. The default action is *drop*.

**(C2) Outcome-Grounded Confidence with physical gating.** The confidence update path on any advised action must be foreign-key-constrained on the arrival of a physical-floor reward R_phys. The compliance indicator `[a_t = â_t]` enters the update **only as a gating scalar**, never as a reward source. This is the architectural firewall against the reflexivity trap. Wire it at the database schema level, not as a code convention.

**(C3) Withhold-sampling as a first-class subsystem.** Build the withhold coordinator into the Twin advisory layer from day one. Ship Phase 1–2 with `p_withhold = 0`. Turn it on at 0.05 once advisory guidance is being delivered (Phase 3+) and calibrate. The counterfactual distribution Q′ must be storable and queryable separately from Q from day one.

---

## 2. The CIAER+ Schema — Atomic Data Unit

A CIAER+ event record is a single JSON object representing one complete expert decision cycle from perceptual trigger through outcome evaluation. Every field exists to defeat a specific failure mode in AI learning from human expertise. No field is decorative.

### 2.1 Top-level structure

```
{
  "schema_version": "1.0",
  "event_id": "uuid-v4",
  "envelope":   { ... },   // identity, provenance, graph linking
  "pre_env":    { ... },   // pre-cause environmental layer (I-frame context)
  "cause":      { ... },   // perceptual trigger — what was perceived
  "intuition":  { ... },   // causal model — what the expert believed
  "action":     { ... },   // intervention — what was done
  "shadow_actions": [ ... ], // rejected alternatives — first-class siblings
  "effect":     { ... },   // environmental response — what changed
  "result":     { ... }    // model update — confirmed or revised
}
```

### 2.2 Field status conventions

| Status | Source | Meaning |
|---|---|---|
| **REQUIRED** | Operator + system | Must be present. Absence breaks the causal chain and invalidates the record for ML training. |
| **OPTIONAL** | Operator + sensors | Enriches the record when available. Null is valid and handled by training pipelines. |
| **DERIVED** | System computed | Automatically calculated. Never entered manually. Always present in complete records. |

### 2.3 Phase fields (abbreviated — full reference in `arcshield_schema_ref.docx`)

**ENVELOPE:** `schema_version`, `event_id`, `timestamp_start`, `operator_id`, `facility_id`, `line_id`, `parent_event_id` (optional), `parallel_event_ids` (optional, multi-expert divergence)

**PRE_ENV:** `shift_phase`, `material_batch_id`, `ambient_temp_f`, `recent_events_summary`, `crew_state_tag`. This is the I-frame: shift-start baseline against which all subsequent deltas are computed.

**CAUSE:** `capture_timestamp`, `trigger_source` (TIER_1_ACCELEROMETER | TIER_2_GAZE_DWELL | TIER_3_BIOMETRIC | OPERATOR_INITIATED — typically multi-tier), `sensor_readings` (array of `{instrument_id, value, unit, confidence}`), `biometric_snapshot` (HR, HRV-RMSSD, nonlinear HRV features, accelerometer activity class), `acoustic_profile` (spectral delta from baseline), `gaze_dwell_duration_sec`, `visual_anchor_description`

**INTUITION:** `srk_level` (SKILL | RULE | KNOWLEDGE, per Rasmussen 1983), `causal_hypothesis` (free text), `failure_mode_tag` (controlled vocabulary — first taxonomy entry is `material_segregation_funnel_flow`), `confidence_level` (0–1), `projection` (what happens if uncorrected), `voice_transcript` (verbatim ASR), `biometric_signature` (delta vector at moment of inference)

**ACTION:** `action_type` enum, `action_timestamp`, `action_rationale`, `action_sequence` (ordered array of `{step_id, description, parameter_changed, from_value, to_value, rationale}`)

**SHADOW_ACTIONS:** array of `{action_type, rejection_rationale, confidence_in_rejection}`. **This is a first-class structured field, not a free-text note.** A behavioral cloning system that sees only chosen actions has no signal on what was considered and rejected; SHADOW_ACTIONS supplies that signal.

**EFFECT:** `capture_timestamp`, `sensor_readings`, `deltas` (DERIVED: per-instrument `{delta, direction: IMPROVED|DEGRADED|UNCHANGED}`), `prediction_match` (CONFIRMED|PARTIAL|DISCONFIRMED|INDETERMINATE)

**RESULT:** `completed_at`, `outcome_tag` (PROBLEM_PREVENTED | PROBLEM_RESOLVED | PARTIAL_RESOLUTION | NO_CHANGE | WORSENED | TOO_EARLY), `escalation_state_at_result`, `escalation_delta` (DERIVED), `hypothesis_confirmed` (bool), `model_revision` (OPTIONAL — only if hypothesis_confirmed is false; high-value learning input), `product_quality_impact`, `graph_weight` (DERIVED 0–1)

### 2.4 Schema invariants enforced at write time

- An event record with `prediction_match = INDETERMINATE` and no `pending_R_phys` deadline is rejected. Every event must either close on its own telemetry or be queued for deferred R_phys arrival.
- `shadow_actions` may be empty for SKILL-level events but is REQUIRED at KNOWLEDGE-level. KNOWLEDGE without rejected alternatives is suspicious and should be flagged for re-elicitation.
- `model_revision` is REQUIRED when `hypothesis_confirmed = false`. False events without revisions are useless to the learning system.

---

## 3. The mp4Real™ Container

### 3.1 Format choice: fragmented MP4 (fMP4) with timed-metadata tracks

Do not invent a new container. Use ISO BMFF (fragmented MP4) with the following track layout. This buys free PTS handling, standard muxing tools (FFmpeg / MP4Parser / ExoPlayer), and downstream interoperability with anyone who can read an MP4.

| Track | Codec / Format | Source | Sample rate |
|---|---|---|---|
| 1 — POV video | H.265 (HEVC), hardware encoded | CameraX (Gen 1 phone) → Meta Ray-Ban (Gen 2) | 30 fps, 1080p |
| 2 — Acoustic | AAC | Phone mic / hearing-protection mic | 48 kHz |
| 3 — Vibration / accelerometer | `application/octet-stream` timed metadata | Phone IMU | ~100 Hz (hardware ceiling) |
| 4 — Biometric | `application/octet-stream` timed metadata | **Polar H10 / Verity Sense via PMD** | ECG 130 Hz (H10), HR ~1 Hz, R-R per beat |
| 5 — Thermal | `application/octet-stream` timed metadata | Open-Meteo (Gen 1 proxy) / BT IR thermometer (Gen 2) | Variable |
| 6 — Voice annotation | `application/octet-stream` timed metadata | On-device ASR transcript with word-level timestamps | Event-driven |
| 7 — Process telemetry | `application/octet-stream` timed metadata | PLC API (Gen 2+) | Variable per channel |

Matroska is more flexible but has weaker mobile tooling. The trade is not worth it.

### 3.2 PTS and ε_sync (synchronization tolerance)

ε_sync is the hidden risk no one talks about. The Polar wearable ↔ phone clock skew is now sample-indexed via PMD packets (much cleaner than the Pixel Watch ↔ Wear OS bridge). The phone clock anchors all non-Polar tracks via `elapsedRealtimeNanos()`.

**Implementation rules:**

- At shift start, perform an NTP-style handshake between phone and any external sensor that has its own clock. Persist offset. Resync every 5 minutes.
- Anchor everything on the phone side to `elapsedRealtimeNanos()` (monotonic, immune to wall-clock adjustments).
- For Polar: anchor to PMD sample-index timestamps at first packet receipt; advance by sample index thereafter.
- Write the observed ε_sync per session into the container metadata (`udta` box). Downstream consumers (LLR gate, codebook matcher, Twin) must be able to query and flag low-confidence windows.
- Target ε_sync ≤ 100 ms. Anything above 250 ms invalidates the window for primitive matching but still enters the corpus with a `low_sync_confidence` flag.

### 3.3 I-frame / P-frame semantics

- **I-frame:** 60–120 seconds of all-channel baseline recorded at shift start. Sample written at PTS=0 across all tracks. This is `pre_env` made manifest at the container level. The LLR gate's null hypothesis H₀ is parameterized from this baseline.
- **P-frame:** the [t − W_pre, t + W_post] window emitted when Λ ≥ τ. Default W_pre = 30 s, W_post = 60 s. Tunable per facility.

---

## 4. The LLR Gate (Λ_env, Λ_bio, τ)

### 4.1 Phase 1 implementation: streaming statistical features, not a learned model

A learned LLR gate sounds attractive and is the wrong move for Phase 1. You cannot train it without a corpus, and you cannot get a corpus until the gate works. Bootstrap with closed-form streaming statistics.

**Λ_env components** (each contributes a log-likelihood under H₀ ~ shift-start baseline vs. H₁ ~ "something changed"):

- Acoustic spectral KL divergence vs. baseline spectrum
- Accelerometer RMS over a rolling 5-second window
- Motion energy (frame-to-frame differencing on the POV track)
- Gaze dwell duration on a single anchor (sustained-attention proxy)

**Λ_bio components:**

- HR delta vs. 5-minute rolling baseline
- HRV-RMSSD ratio vs. 5-minute baseline
- Nonlinear HRV deviation (SD1/SD2, sample entropy) vs. shift-start baseline — **now defensible because Polar gives you raw R-R intervals; this was aspirational under Pixel Watch**
- Accelerometer-gated activity class (resting | light | moderate | vigorous). High activity *gates down* Λ_bio because cardiovascular signal during physical work is confounded.

### 4.2 Combination rule

Under the conditional-independence assumption **S_env ⊥ S_bio | E, Z** (Z = nuisance variables like ambient heat from a 32:1 L/D barrel), the components sum:

```
Λ = Λ_env + Λ_bio
fire when Λ ≥ τ
```

Use the Kay 1998 generalized likelihood ratio approximation for composite hypotheses. This is honest and citable in the patent.

### 4.3 Calibration protocol

For the first two weeks of any deployment:

1. Run the gate in **shadow mode**. Log every candidate window. Trigger nothing.
2. Hand-label true positives vs. false positives at end of shift.
3. Tune τ to target ~80% true-positive rate / ≤20% false-positive rate.
4. Lock τ. Begin live capture.

### 4.4 Phase 3+ upgrade path

Replace the closed-form gate with a learned classifier once ~200 validated events exist. Keep the closed-form gate available as a fallback and as a sanity check on the learned gate's drift.

---

## 5. The Codebook (Behavioral Primitive Map)

### 5.1 Phase 1: hand-curated dict

For corpus depth < ~100 events, the codebook is a Python dict keyed by `failure_mode_tag`, matched by cosine similarity on per-track summary embeddings. This is debuggable, inspectable, and you can read every entry. Premature RVQ is a research project, not a Phase 1 ship.

### 5.2 Phase 3+: residual vector quantization (RVQ)

Build the learned codebook as:

1. Per-track causal Transformer encoder → fixed-dim embedding per (W_pre + W_post) window
2. Joint contrastive training across the K tracks (VICReg or similar)
3. Residual VQ with ~3 stages of 256 codes each

The CIAER+ DAG is a structural overlay on each codebook entry, **not** part of the quantizer. The quantizer compresses sensor patterns; the DAG carries the symbolic causal structure.

### 5.3 Encode/match logic (per FIG. 8)

For each captured event window:

1. Compute per-track summary embeddings.
2. For every primitive π_i in the codebook, compute conditional total correlation `TC(T_1, ..., T_K | π_i)`.
3. Select `π* = argmax_i TC(T_1, ..., T_K | π_i)`.
4. **If `TC* ≥ θ_TC`:** emit compressed primitive-reference token (index of π* + Δ-deviation vector). Storage cost is tiny.
5. **If `TC* < θ_TC`:** store full uncompressed multi-track waveform. Queue for human-in-the-loop validation. On validation, if a coherent CIAER+ structure emerges that doesn't match any π_i, the codebook expands: π_{n+1} is added.

This is MDL operating directly: novel events cost bits, familiar events cost references.

---

## 6. Outcome-Grounded Confidence — The Anti-Reflexivity Firewall

### 6.1 The formal update (per FIG. 9)

```
δ = R_phys(T_{t+Δt}) − C_t(â_t | s_t)
C_{t+1}(â_t | s_t) = C_t(â_t | s_t) + α · δ · [a_t = â_t]
```

Where:
- `R_phys(T_{t+Δt})` is a continuous or binary scalar derived from the multiplexed telemetry track T, measuring whether the physical state transitioned to spec, independent of operator policy.
- `[a_t = â_t]` is the **gating scalar** indicating operator compliance with advised action. **It is never a reward source.** It only determines whether this update applies at all.
- `α` is the learning rate.

### 6.2 Architectural enforcement

The reason this is architectural and not a code convention:

```
Event record           ──┐
  event_id              │
  advised_action â_t    │  cannot update C
  observed_action a_t   │  without FK to
  compliance gate scalar│  pending_R_phys
  pending_R_phys ─────────┘
                         │
                         ▼
                    R_phys arrival
                         │
                         ▼
                    Confidence update
                    (only path that
                     mutates C)
```

Implement `R_phys` as a deferred update queue keyed by `(event_id, telemetry_arrival_deadline)`. Some outcomes resolve in seconds (motor amps recover). Others resolve in hours (lab QC on the extruded product). The schema carries `pending_R_phys` deadlines and explicit timeout handling. If a deadline passes without R_phys arrival, the event is marked `INDETERMINATE` and **does not update C**.

### 6.3 Conditional independence guarantee

```
R_phys(T_{t+Δt})  ⊥⊥  [a_t = â_t]  |  s_{t→t+Δt}
```

No information path is permitted in code that lets operator compliance be evidence of physical success. The compliance bit and the reward function read from completely separate columns / events / tables.

---

## 7. The Withhold-Sampling Subsystem (Reflexivity Detector)

### 7.1 What it does (per FIG. 10)

Once the Twin issues advisory guidance, the system maintains three distributions in parallel:

- **P** — pre-Twin expert baseline corpus (frozen reference)
- **Q** — live distribution under Twin guidance
- **Q′** — withheld counterfactual distribution

Compute `D_KL(P ‖ Q)` continuously. On D_KL spike, trigger withhold-sampling policy: randomly withhold high-weight guidance on a calibrated subset of matched events. The operator acts from their own generative model. Those Cause-to-Result chains enter the corpus tagged as counterfactual samples and update Q′ instead of Q.

Persistent divergence between Q and Q′ is the signal that graph weights have decoupled from operational reality and re-weighting is required.

### 7.2 Implementation requirements

- Build the withhold coordinator into the Twin advisory layer from day one.
- Ship Phase 1–2 with `p_withhold = 0`. The pipeline exists but does not fire.
- When advisory guidance goes live (Phase 3+), default `p_withhold = 0.05`. Make it a deployment parameter, not a constant.
- Maintain Q and Q′ as separate tables / graph subgraphs from creation. Never merge them.
- Surface D_KL(P‖Q), D_KL(P‖Q′), and D_KL(Q‖Q′) on the operator dashboard. The divergence between Q and Q′ is the most informative signal.

---

## 8. Polar Biometric Integration (Current Decision)

The biometric channel switched from Pixel Watch 4 + Health Connect to **Polar wearables via the PMD (Polar Measurement Data) protocol**. This is an upgrade, not a downgrade.

### 8.1 What improved

- Raw ECG at 130 Hz on the H10 → genuine beat-to-beat R-R intervals → nonlinear HRV features (SD1/SD2, sample entropy, DFA-α1) become defensible rather than aspirational.
- Clock sync is cleaner: PMD packets carry sample-level timestamps. Anchor to `elapsedRealtimeNanos` at first packet receipt and advance by sample index.
- PPE integration is more practical: H10 sits cleanly under a high-vis vest; Verity Sense on the bicep doesn't interfere with gloves.

### 8.2 What is permanently off the wearable

- **EDA.** Polar does not do it on H10 or Verity Sense. The whitepaper has been honest that raw cEDA was never available on Pixel Watch either; document EDA as "channel deferred to companion sensor (Emotibit / Shimmer GSR+)" behind the same `BiometricSource` interface. Do not design it out.
- **Wear OS Activity Recognition.** Compute activity classification phone-side from phone accel, optionally fused with Polar's onboard accel stream.
- **Glanceable UI on the wrist.** No haptic confirmation channel on the device. Fall back to audio chirp through earbuds or phone haptic.

### 8.3 Device model selection

- **H10 (chest strap, true ECG at 130 Hz):** validation-phase ground truth. Use during calibration sprints and for publication-grade nonlinear HRV data.
- **Verity Sense (optical armband):** the daily operational device. More wearable for an 8-hour shift but loses ECG morphology.
- **Strategy:** run both during calibration, characterize Verity-vs-H10 drift on R-R, then ship Verity Sense for daily wear with periodic H10 spot-checks.

### 8.4 SDK choice

Use Polar's official `polar-ble-sdk` (Kotlin, coroutine flows) as the primary integration. Fall back to raw GATT against documented PMD UUIDs only if a third-party dependency is removed later.

### 8.5 BLE link stability around 480V motors

This is real. Polar's link layer is solid but dropouts will happen near induction heaters and large motors.

**Required handling in `PolarBleBiometricSource`:**

- Buffer aggressively on the phone side.
- Bake reconnect logic into the source.
- H10 holds ~30 minutes of data internally on link loss — implement back-fill sync on reconnect.
- **Never silently interpolate across a dropout.** Emit explicit `BiometricGap(start, end, reason)` events into the timed-metadata track. Downstream consumers (LLR gate, codebook matcher) must see gaps flagged, not smoothed.
- ε_sync contract: a four-second BLE gap is a real event. It flags the surrounding window with `low_sync_confidence = true`.

---

## 9. Provider Abstraction Interfaces (Kotlin)

Every hardware channel is consumed through an interface. The Gen 1 (phone-mounted + Polar) implementation must swap to Gen 2 (Meta Ray-Ban + Polar + PLC) with zero structural changes to consumers.

```kotlin
// All sources expose flows of timestamped samples.
// Timestamps are elapsedRealtimeNanos on the phone side.

interface CaptureSource {
    fun videoFrames(): Flow<VideoFrame>      // POV track
    fun audioFrames(): Flow<AudioFrame>      // acoustic track
    val sourceId: String                     // "phone_cameraX_v1" | "meta_raybans_v1"
}

interface BiometricSource {
    fun heartRate(): Flow<HrSample>
    fun rrIntervals(): Flow<RrSample>        // empty flow if device doesn't support
    fun ecgWaveform(): Flow<EcgSample>       // empty flow if device doesn't support
    fun accelerometer(): Flow<AccelSample>
    fun edaWaveform(): Flow<EdaSample>       // empty flow if device doesn't support
    fun gaps(): Flow<BiometricGap>           // explicit, never silent
    val capabilities: Set<BiometricChannel>
    val sourceId: String                     // "polar_h10_v1" | "polar_verity_sense_v1" | "emotibit_eda_v1"
}

interface PreEnvSource {
    suspend fun captureBaseline(): PreEnvSnapshot   // 60–120s at shift start
    fun ambientTemp(): Flow<TempSample>
    val sourceId: String
}

interface CorpusSink {
    suspend fun persistEvent(event: CiaerPlusEvent): Result<EventId>
    suspend fun persistContainer(uri: Uri, eventId: EventId): Result<Unit>
    suspend fun queuePendingRPhys(eventId: EventId, deadline: Instant)
}

interface LlmClient {
    suspend fun elicit(prompt: ElicitationPrompt, context: SensoryContext): ElicitationResponse
    suspend fun generateGuidance(query: GuidanceQuery): TwinGuidance
    val providerId: String                   // "claude" | "gemini" | "local_llama"
}

interface PlcTelemetrySource {
    fun channel(channelId: String): Flow<TelemetrySample>
    suspend fun snapshotAt(t: Instant): TelemetrySnapshot
    val availableChannels: Set<String>
}
```

**Rule:** every channel takes an interface. No consumer ever holds a reference to a concrete hardware class. The Gen 1 → Gen 2 swap is a DI binding change, not a structural rewrite.

---

## 10. Recommended Repository Structure

```
arcshield/
├── CLAUDE.md                          # this file
├── README.md
├── android/
│   ├── app/                           # Compose entry point + DI
│   ├── core-capture/                  # ring buffer, mux pipeline, ε_sync
│   ├── core-codec/                    # mp4Real container, fMP4 writer
│   ├── core-llr/                      # Λ_env, Λ_bio, τ gate
│   ├── core-schema/                   # CIAER+ data classes, validators
│   ├── source-camerax/                # CaptureSource: phone camera
│   ├── source-polar/                  # BiometricSource: H10 / Verity Sense
│   ├── source-emotibit/               # BiometricSource: EDA (deferred)
│   ├── source-meta-raybans/           # CaptureSource: Gen 2 glasses
│   ├── source-openmeteo/              # PreEnvSource: ambient temp proxy
│   ├── source-plc/                    # PlcTelemetrySource: facility integration
│   ├── llm-claude/                    # LlmClient: Anthropic
│   ├── llm-gemini/                    # LlmClient: Google
│   └── debrief-ui/                    # Compose screens for HITL validation
├── backend/
│   ├── ingest/                        # container upload + demux
│   ├── codebook/                      # primitive map, TC matcher
│   ├── graph/                         # Kuzu (Phase 1) → Memgraph (scale)
│   ├── ogc/                           # R_phys deferred queue + C updater
│   ├── withhold/                      # Q / Q′ partitioning, D_KL monitor
│   ├── twin/                          # RAG (Phase 2) → LoRA (~200 events)
│   └── api/                           # gRPC / REST surface for the app
├── ciaer-ql/                          # Cypher-compatible probabilistic query layer
├── ml/
│   ├── augmentation/                  # physically-constrained Monte Carlo
│   ├── embedding/                     # per-track encoders
│   ├── rvq/                           # residual VQ codebook (Phase 3+)
│   └── lora/                          # Twin adapter training (Phase 3+)
├── docs/
│   ├── whitepaper/                    # v10.x sources
│   ├── patent/                        # provisional + figures
│   └── ops/                           # deployment guides
└── tools/
    ├── shadow-mode-labeler/           # tooling for the calibration sprint
    └── codebook-inspector/            # dict viewer for Phase 1 codebook
```

---

## 11. Build Order with Acceptance Criteria

Phases are gates. Do not advance until acceptance criteria are met.

### Phase 1: Container + LLR gate, shadow mode (weeks 1–6)

**Deliverables:**

- `core-capture`, `core-codec`, `core-llr` modules functional
- `source-polar` (H10) consuming PMD streams
- `source-camerax` POV video
- Phone-side mux pipeline writing fMP4 with all five Phase 1 tracks (POV, acoustic, vibration, biometric, voice)
- LLR gate operates in shadow mode (logs candidate windows, never persists a full container)
- ε_sync measured and logged per session

**Acceptance criteria:**

- ε_sync sustained ≤ 100 ms across at least 5 production shifts at PPVC Line 1
- 20–30 candidate windows logged
- Hand-labeling tool (`tools/shadow-mode-labeler`) operational
- No BLE-induced data corruption — all dropouts emit explicit `BiometricGap` records

**Hard stop:** if ε_sync exceeds 250 ms persistently, halt and debug Polar↔phone clock anchor before proceeding.

### Phase 2: Backend + manual codebook (weeks 6–12)

**Deliverables:**

- Backend `ingest`, `codebook` (hand-curated dict), `graph` (Kuzu embedded) running on a single VPS or Mac mini
- Manual validation UI in `debrief-ui`
- CIAER+ schema validators enforced at write time (schema invariants from §2.4)
- OGC schema wired with `pending_R_phys` deferred queue (R_phys input from manual QC entry)
- Twin advisory layer wired but disabled

**Acceptance criteria:**

- 50+ validated CIAER+ events in the corpus
- At least one event with `hypothesis_confirmed = false` and a `model_revision` entry (proves the system can ingest disconfirmation)
- At least one multi-expert divergence pair (`parallel_event_ids` populated) — even if one expert is Kahn at two different times
- OGC update path passes a contrived adversarial test: a high-compliance / low-R_phys event must produce negative δ

### Phase 3: PLC integration + embedding match + RAG Twin (months 3–6)

**Deliverables:**

- `source-plc` consuming one production-data channel from Hollowell PLC infrastructure
- Per-track summary embeddings in `ml/embedding`
- Codebook matcher uses cosine-similarity over embeddings (replaces dict lookup)
- RAG-based Twin advisory live (no LoRA yet)
- Withhold coordinator instrumented; `p_withhold = 0` in config

**Acceptance criteria:**

- R_phys auto-computed from at least one PLC channel
- Twin RAG retrieval surfaces top-3 historically similar events with full CIAER+ chains
- Withhold coordinator can be toggled on with `p_withhold = 0.05` without code change (config-only)
- 150+ events in corpus

### Phase 4: RVQ codebook + LoRA Twin + active withhold-sampling (months 6–12)

**Deliverables:**

- RVQ-trained codebook replacing cosine-similarity matcher
- LoRA adapter trained on accumulated CIAER+ corpus
- Active withhold-sampling at `p_withhold = 0.05`
- Q / Q′ partitioned graph storage
- D_KL(P‖Q), D_KL(P‖Q′), D_KL(Q‖Q′) dashboard live

**Acceptance criteria:**

- 200+ events
- Twin guidance has measurable accuracy on held-out validation events
- At least one full withhold-sampling cycle has triggered re-weighting on a primitive

### Phase 5: Multi-facility + Twin marketplace (months 12+)

Out of scope for this handoff. Read `arcshield_marketplace_addendum.docx` when this phase opens.

---

## 12. Critical Constraints & Traps — Things Not to Do

These are mistakes a fresh instance is statistically likely to make. Don't.

1. **Don't make the edge app a recorder.** The default is *drop*. Capture is the exception path. If you find yourself building a "record everything, filter later" architecture, you have misunderstood §1.
2. **Don't update C without R_phys.** The OGC firewall (§6) is enforced at the schema layer with foreign-key constraints, not as a code convention. Any code path that mutates confidence without an `R_phys` arrival is a critical bug, not a feature.
3. **Don't silently interpolate across BLE dropouts.** Emit explicit `BiometricGap` events. The corpus has to see the gap.
4. **Don't string-match `failure_mode_tag` from free text.** Use the controlled vocabulary. New tags require explicit registration in the domain ontology, not an LLM guess.
5. **Don't conflate Q and Q′.** Counterfactual samples live in a separate table / subgraph from the moment of creation. Never merge.
6. **Don't ship a learned LLR gate in Phase 1.** Closed-form streaming statistics. Calibrate τ empirically. The learned gate is a Phase 3+ upgrade.
7. **Don't use Health Connect for biometrics anymore.** The Polar PMD path replaces it. If you find Health Connect code, it is dead.
8. **Don't store voice transcripts as part of Monte Carlo augmentation.** Augmentation operates **only** on the structured sensor-derived components. Voice annotation content is never fabricated.
9. **Don't write `[a_t = â_t]` into the reward column of any table.** It belongs in the *gating* column. They are not the same column.
10. **Don't reproduce copyrighted content from referenced papers in code comments or docs.** Cite, summarize, paraphrase.
11. **Don't omit the IP / trademark notice from any released artifact.** §15 has the exact block.
12. **Don't ask the user to clarify things this CLAUDE.md already answers.** Re-read first.

---

## 13. Patent Figures as Architectural Ground Truth

`ArcShield_Patent_Drawings_Figs610.pdf` contains Figs 6–10. These are not illustrations — they are reference implementations expressed in diagram form. If implementation diverges from a figure, **the figure wins** and the implementation gets corrected.

- **FIG. 6** — mp4Real container layout. Tracks 601–605 with PTS 606 and ε_sync 607. I-frame 608 / P-frame 609 semantics. W_pre 610 / W_post 611.
- **FIG. 7** — Cyber-physical LLR gating flow. Λ_env (701) and Λ_bio (702) computed independently under S_env ⊥ S_bio | E, Z (705). Sum gate (706). Threshold τ (707). Windowed write (708) or buffer drop (709).
- **FIG. 8** — Behavioral codebook compression. TC matcher (803), argmax selection (804), compressed token emission (805) or uncompressed waveform storage (806). HITL validation (807) drives codebook expansion (808).
- **FIG. 9** — Outcome-Grounded Confidence flow. Compliance scalar (903) is a *gate*, not a reward source. Physical reward R_phys (905) is the only input to δ (906). Update rule (907). Conditional independence guarantee (908).
- **FIG. 10** — Withhold-sampling and KL divergence drift detector. P (1001) vs. Q (1002). D_KL (1003). Threshold gate (1004). Withhold policy (1005). Counterfactual sample collection (1006). Re-estimation feedback (1007).

---

## 14. Author Principles & Working Patterns

Kahn operates at graduate-seminar technical level. Calibrate accordingly.

- **No basics explanations** in ML, imitation learning, RL, LLM architecture, embodied cognition, predictive processing, or polymer processing. He has the background.
- **Concise, direct.** Lead with the answer. No preamble. Minimal hedging. State assumptions inline rather than asking for clarification when an assumption is reasonable.
- **Operationally tight.** Few exploratory detours. Direct movement toward deliverables.
- **Adversarial stress-testing before publication.** The 9-counterexample lineage (Venus flytrap, E. coli chemotaxis, bimetallic thermostat, patellar reflex, rolling rock, chess grandmaster, sterile expert, AI system trained on expert data, fast-food shift manager) is precedent. New core claims get stress-tested by counterexample before they enter the spec.
- **Rigor over marketing.** Domain-neutral technical language. Speculative elements are flagged explicitly (`⚠ Speculative / Research-Dependent` tags in arcshield_product_architecture.docx). Aspirational features are never presented as built features.
- **Single-file deliverables when possible.** Prefers comprehensive single-file handoffs (like this one) over multi-doc sprawl.
- **Mobile-friendly output.** Many sessions are from a phone. Long prose, dense formatting, and over-bulleting are friction.
- **Verification before finalization.** Document versions are built programmatically with explicit structural checks. Acceptance criteria are tested before phase advancement.

When in doubt: produce the deliverable, flag the speculative parts, and trust him to push back on what doesn't fit.

---

## 15. Required IP / Trademark Notice

This exact block must appear in:

- The README.md of any released repository
- The footer of any whitepaper, technical document, or marketing artifact
- The header comment of any source file released to a third party

```
Intellectual Property and Trademark Notice

mp4Real™, ArcShield™, CIAER™, and CIAER+™ are trademarks of Capps Consulting
Company LLC. The multi-track cyber-physical capture architecture, the
application of log-likelihood ratio (LLR) gating to multimodal industrial
decision events, and the behavioral codebook discretization methods described
in this document are the proprietary intellectual property of Kahn Capps and
Capps Consulting Company LLC. Unauthorized commercial use, reproduction, or
implementation of the mp4Real™ container architecture or the CIAER™ and CIAER+™
schemas without explicit licensing is prohibited. All rights reserved.
```

---

## 16. Glossary

| Term | Definition |
|---|---|
| **mp4Real™** | The reality codec — fMP4-based multi-track container plus LLR gate plus codebook. |
| **CIAER / CIAER+** | The five-phase decision event schema (Cause · Intuition · Action · Effect · Result). CIAER+ extends with Pre-ENV layer and Shadow Actions. |
| **PIE** | Perceptual Inference Elicitation. The primary patentable claim: multimodal inference of expert knowledge state from sensory input plus calibrated elicitation prompts. |
| **LLR gate** | Log-likelihood ratio gate. Decides whether a captured window is worth muxing into the container. Λ_env + Λ_bio ≥ τ. |
| **ε_sync** | Synchronization tolerance across multiplexed tracks under shared Presentation Timestamp. Target ≤ 100 ms. |
| **I-frame** | Shift-start baseline across all channels. PTS=0 in the container. |
| **P-frame** | Captured event window. Multi-channel delta from the I-frame. |
| **OGC** | Outcome-Grounded Confidence. The update rule that anchors C in physical floor outcomes (R_phys), not operator compliance. |
| **R_phys** | Physical reward function. Scalar derived from telemetry, measuring physical state transition to spec. The only legitimate input to confidence updates. |
| **TC** | Total correlation. Multi-information across K tracks, conditional on a primitive. Used as the matching score in the codebook. |
| **Shadow Actions** | Rejected alternatives. First-class structured field, captured as data siblings to the chosen action. |
| **Withhold-sampling** | Mitigation against the reflexivity trap. Twin randomly withholds high-weight guidance at rate `p_withhold` to collect counterfactual samples (Q′). |
| **P / Q / Q′** | Reference distribution / live distribution / withheld counterfactual distribution. KL divergences among these drive re-weighting. |
| **SRK** | Skill / Rule / Knowledge. Rasmussen's 1983 hierarchy. Tags Intuition phase content. |
| **PPVC Line 1** | The first validation site at Hollowell Industries. PVC extrusion. |
| **Personal AI Twin** | LoRA-adapter model trained on a single operator's CIAER+ corpus. Operator-owned. Portable. |
| **Craft Cortex / Org Cortex / Super-Corpus** | Layer 2a / 2b / 3 aggregate intelligence layers. Sealed against each other architecturally. See `arcshield_product_architecture.docx`. |

---

## 17. Open Questions / Known Risks (as of v10.4)

These are documented for awareness, not for resolution in this handoff. Do not act on them without explicit instruction.

1. **Biometric signal reliability in industrial environments** — can HRV-RMSSD reliably distinguish cognitive decision events from physical exertion and thermal exposure, even with Polar's improved signal? Resolution path: empirical calibration during extended PPVC deployment with H10 ground truth.
2. **LoRA corpus size threshold** — preliminary estimate 200–500 events for a meaningful Twin. Unknown until tested.
3. **Cortex aggregation privacy** — federated learning vs. differential privacy not yet specified. Specialist ML engineering required.
4. **Gaze dwell false-positive rate** — head movement vs. sustained attention discrimination needs accelerometer gating. Ongoing calibration.
5. **EDA absence** — Polar doesn't provide it. Emotibit / Shimmer GSR+ companion sensor is the documented path; not yet integrated.
6. **PLC integration scope** — exact Hollowell PLC API surface is not yet mapped. One channel for Phase 3 deliverable; full integration deferred.
7. **EU AI Act compliance** — high-risk workplace AI classification requires conformity assessments, technical documentation, human oversight provisions, transparency obligations. On-device processing addresses some requirements; full compliance work is outstanding.
8. **Counterfactual sampling rate** — `p_withhold = 0.05` is a starting point. Empirical calibration required before Phase 4 launch.

---

## 18. Re-entry Protocol

When picking up this repository cold:

1. Read this CLAUDE.md in full.
2. Re-read §1 (central insight) and §6 (OGC firewall). Those two sections cause the most regressions.
3. Look at the current `README.md` and `phases/CURRENT_PHASE.md` (if it exists) for live state.
4. Re-read the schema in `arcshield_schema_ref.docx` only if working in `core-schema` or `backend/ingest`.
5. Re-read the patent figures only if working in `core-codec`, `core-llr`, `backend/codebook`, `backend/ogc`, or `backend/withhold` — those four subsystems are figure-bound.
6. Do not change architectural decisions documented here without explicit author sign-off. If you believe a decision is wrong, flag it for discussion, don't unilaterally rework it.

---

*End of CLAUDE.md. This file supersedes any conflicting guidance in source comments, READMEs, or older handoff documents. If something contradicts this file, fix the other thing.*
