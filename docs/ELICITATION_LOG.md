# ELICITATION_LOG.md

**This file is the PIE method turned inward on the architect.** It is the same elicitation protocol the ArcShield system runs on operators — multimodal context + calibrated prompt — applied here to surface the *rules that produced your past decisions* so they can be promoted into SELECTION_PRINCIPLE.md.

Each entry below is a question where, having read everything in the project, I cannot reliably reconstruct the rule that produced your choice. The options are real alternatives that you considered or that a reasonable engineer would consider in your position. Pick one (or write a different answer if none fit), give a one-line rationale, and the rule gets promoted to the right section of SELECTION_PRINCIPLE.md.

> **How to answer.** For each question, write your choice in the **Answer** field and the rule that produced it in the **Inferred Rule** field. The Inferred Rule is the transferable principle — written so a successor facing a *new* decision in the same domain can apply it. Drop the answers in as comments, voice notes, or a quick session — answer style matters less than getting the rule externalized.

> **Triage.** The questions are tagged `[BLOCKING]`, `[HIGH]`, `[MEDIUM]`, or `[LOW]` by how much the answer constrains future build work. Answer `[BLOCKING]` first.

---

## §1 — Codec & Container

### Q1.1 — `[ANSWERED 2026-05-26]` H.265 vs H.264 for the POV track

**Context.** CLAUDE.md §3.1 specifies H.265 (HEVC). H.264 (AVC) is more universally supported and has less encoding cost on lower-end hardware.

**Options:**
- (a) H.265 — chosen for compression efficiency at the POV bitrate
- (b) H.264 — chosen because broader decoder support matters more than bitrate
- (c) H.264 for Gen 1, H.265 when Meta Ray-Ban hardware accelerates it
- (d) Other — please specify

**Answer:** (c) H.264 for Gen 1; H.265 at Gen 2 when Meta Ray-Ban hardware accelerates it.
**Inferred Rule:** → SELECTION_PRINCIPLE.md §4.1 — codec tracks hardware-accelerated availability + decoder ubiquity at the current tier, not peak compression. **NOTE:** current code encodes HEVC; needs HEVC→AVC swap for Gen 1.

---

### Q1.2 — `[ANSWERED 2026-05-26]` Acoustic sample rate — 48 kHz vs 16 kHz

**Context.** CLAUDE.md §3.1 specifies AAC at 48 kHz. 16 kHz is sufficient for voice transcription and machine acoustic signatures up to 8 kHz, and saves significant storage.

**Options:**
- (a) 48 kHz — preserves spectral content above 8 kHz for future analysis we haven't yet specified
- (b) 16 kHz — sufficient for the analyses we know we need; storage budget matters
- (c) 48 kHz on the environmental track, 16 kHz on the voice annotation track
- (d) Other

**Answer:** (c) 48 kHz environmental track, 16 kHz voice-annotation audio.
**Inferred Rule:** → SELECTION_PRINCIPLE.md §4.1 — sample rate set per track by that track's analyses (richness on environmental, economy on voice). Current 48 kHz acoustic track is correct; no immediate code change (voice is a transcript metadata track today, not raw audio).

---

### Q1.3 — `[MEDIUM]` Container fragmentation interval

**Context.** fMP4 fragments are written at intervals. Shorter intervals = lower data loss on crash, higher metadata overhead. Longer intervals = the inverse.

**Options:**
- (a) Per-event fragments — every captured window is its own fragment
- (b) Time-bucketed fragments — e.g., one fragment per shift
- (c) Hybrid — periodic fragments during baseline I-frame, event fragments on Λ ≥ τ
- (d) Other

**Answer:** _____
**Inferred Rule:** _____

---

### Q1.4 — `[MEDIUM]` W_pre and W_post defaults

**Context.** CLAUDE.md §3.3 specifies W_pre = 30 s, W_post = 60 s as default. The asymmetry (more post than pre) implies a rule.

**Options:**
- (a) Operator decisions resolve on a longer timescale than they trigger; the post-window captures the resolution
- (b) Pre-window cost (continuous ring buffer memory) is what bounds W_pre; post-window can be longer because it's bounded only by future events
- (c) W_pre captures the Cause; W_post captures Effect + Result; the schema dictates the asymmetry
- (d) Other

**Answer:** _____
**Inferred Rule:** _____

---

## §2 — Biometrics

### Q2.1 — `[ANSWERED 2026-05-24]` H10 vs Verity Sense as Phase 1 primary

**Context.** ARCHITECTURE_PRINCIPLES.md §4.2 records the validation-vs-operational pattern, but Phase 1 starts with one device. The choice cascades into the LLR gate calibration data.

**Options:**
- (a) H10 first — establish ECG-grounded baselines, then characterize Verity Sense drift against them
- (b) Verity Sense first — the operational device should be calibrated under operational conditions; H10 is a follow-up validation
- (c) Both simultaneously from session one — wear both, log both, decide later
- (d) Other

**Answer:** (d) Both supported from day one. Auto-detect whichever is paired; prompt operator to select if both are detected simultaneously.
**Inferred Rule:** → SELECTION_PRINCIPLE.md §4.2

---

### Q2.2 — `[ANSWERED 2026-05-26]` BiometricSource behavior when both H10 and Verity Sense are paired

**Context.** Both devices stream HR + accel; only H10 streams ECG. The provider abstraction needs a rule for the dual-paired case.

**Options:**
- (a) Prefer H10 for HR/ECG/R-R, prefer Verity Sense for accel — uses each device for its best channel
- (b) Prefer H10 entirely when present, Verity Sense is fallback only
- (c) Require explicit selection at app start — fail closed if ambiguous
- (d) Other

**Answer:** (a) Per-channel best — H10 for HR/ECG/R-R, Verity Sense for accel.
**Inferred Rule:** → SELECTION_PRINCIPLE.md §4.2 — compose the best source per channel, not a primary device. Applies when source-polar's dual-device logic is built (Polar currently decoupled).

---

### Q2.3 — `[MEDIUM]` What counts as a "BLE gap reason"

**Context.** Rule 1.4 says emit `BiometricGap(start, end, reason)` events. The reason field has free-text potential but should be controlled.

**Options:**
- (a) Controlled vocabulary: `LINK_DROPOUT | SENSOR_DETACHED | DEVICE_OFF | BACKFILL_FAILED | UNKNOWN`
- (b) Free text — reasons emerge from incidents
- (c) Controlled vocabulary + free text override field
- (d) Other

**Answer:** _____
**Inferred Rule:** _____

---

### Q2.4 — `[LOW]` Activity classification consumer

**Context.** Phone accel feeds an activity classifier that gates Λ_bio. The classifier output could be (i) consumed only inside Λ_bio, or (ii) emitted into the biometric track for downstream visibility.

**Options:**
- (a) Consume internally only; downstream sees gated Λ_bio
- (b) Emit activity class into biometric track as first-class data
- (c) Emit, but only when activity transitions (not continuous)
- (d) Other

**Answer:** _____
**Inferred Rule:** _____

---

## §3 — Schema & Corpus

### Q3.1 — `[ANSWERED 2026-05-24]` Operator identification across sessions

**Context.** CIAER+ has `operator_id`. The earlier docs use UUID; some patterns suggest a more meaningful structure.

**Options:**
- (a) UUID, mapped to human-readable identity in a separate enterprise/PII table
- (b) Hash of operator + facility — pseudonymous but reproducible across sessions
- (c) Operator-owned key — the operator generates and controls their own ID, marketplace-compatible from day one
- (d) Other

**Answer:** (c) Operator-owned key, provenance only — no privileges attached. Key guarantees record source is always true; grants nothing beyond ability to sign your own events.
**Inferred Rule:** → SELECTION_PRINCIPLE.md §4.3

---

### Q3.2 — `[HIGH]` Versioning of `failure_mode_tag` ontology

**Context.** The controlled vocabulary will grow. CIAER+ events reference tags. When a tag is renamed or refined, old events still point at the old tag.

**Options:**
- (a) Tags are append-only; renames are forbidden; refinements create new tags with a `subsumes` relation to the old one
- (b) Tags can be aliased — old name maps to new name via a translation table
- (c) Tags are versioned per ontology release; events carry the ontology version they were tagged under
- (d) Other

**Answer:** _____
**Inferred Rule:** _____

---

### Q3.3 — `[HIGH]` Threshold for declaring an event "complete"

**Context.** An event needs R_phys before its OGC δ can apply. Some R_phys arrivals take hours (lab QC). What happens if R_phys never arrives?

**Options:**
- (a) Hard deadline at 24 h; missing R_phys → event marked `INDETERMINATE`, never updates C
- (b) Soft deadline depending on outcome type — fast outcomes 1h, slow outcomes 7d
- (c) No deadline — pending forever; flag in dashboard for manual closure
- (d) Other

**Answer:** (d) Hard stop = end of shift (off-shift the operator can no longer contribute). Within the shift, R_phys can also be resolved *deductively* from the operator's continued decision stream: ongoing decisions on the same problem ⇒ still unresolved; cessation ⇒ the physical outcome held (no further action needed until the next issue).
**Inferred Rule:** → SELECTION_PRINCIPLE.md §4.3 — R_phys closure is shift-bounded with a deductive in-shift fallback; telemetry preferred, deduction is a lower-confidence physical-state estimate kept independent of the compliance gate (§6.3).

---

### Q3.4 — `[MEDIUM]` Multi-expert divergence — same operator at different times

**Context.** `parallel_event_ids` records two operators producing different chains from the same Cause. The same operator at different points in their own career is arguably also a divergence pair.

**Options:**
- (a) Treat operator-at-T1 and operator-at-T2 as the same node; divergence requires distinct operators
- (b) Treat as legitimate divergence — the operator's own model evolved; this is high-value learning signal
- (c) Tag separately — `parallel_event_ids` for cross-operator, `temporal_divergence_ids` for self
- (d) Other

**Answer:** _____
**Inferred Rule:** _____

---

### Q3.5 — `[LOW]` `escalation_state_at_result` semantics

**Context.** The escalation ladder is referenced in arcshield_proof_of_method.docx but the integer state values aren't fully specified in the handoff materials.

**Options:**
- (a) 0–4 integer ladder per domain; each domain ontology defines its own ladder
- (b) Universal 0–4 ladder (Normal | Monitor | Investigate | Intervene | Crisis) applied across domains
- (c) State machine, not integers — explicit transition rules between states
- (d) Other

**Answer:** _____
**Inferred Rule:** _____

---

## §4 — Codebook & Learning

### Q4.1 — `[ANSWERED 2026-05-24]` TC threshold θ_TC for codebook match

**Context.** FIG. 8 shows TC ≥ θ_TC → compressed token; TC < θ_TC → uncompressed waveform. The threshold value is empirical. The rule for setting it is not.

**Options:**
- (a) Fixed θ_TC, calibrated once during Phase 1 shadow-mode and locked
- (b) Adaptive θ_TC — adjusts based on codebook density (more crowded → tighter threshold)
- (c) Per-primitive θ_TC — each π_i has its own match threshold based on its variance
- (d) Other

**Answer:** (d) Fixed global θ_TC for Phase 1 (set from shadow-mode hand-labeling), but per-primitive `theta_tc` field in codebook schema from day one — all initialized to global value. Per-primitive divergence is Phase 3+ data change.
**Inferred Rule:** → SELECTION_PRINCIPLE.md §4.4

---

### Q4.2 — `[HIGH]` Codebook primitive retirement

**Context.** A primitive that has never been triggered after N events / M months is evolutionary baggage. Biology prunes. Should the codebook?

**Options:**
- (a) No retirement — codebook is append-only; rare primitives are still meaningful
- (b) Retire at N=0 triggers over M months — purely unused primitives go
- (c) Demote rather than retire — move to a `legacy_primitives` set, no longer matched by default but available on explicit query
- (d) Other

**Answer:** _____
**Inferred Rule:** _____

---

### Q4.3 — `[HIGH]` LoRA training cadence

**Context.** SELECTION_PRINCIPLE.md §4.4 says LoRA at ~200 events. What's the *update* cadence after the first LoRA?

**Options:**
- (a) On corpus delta — retrain when N new events have accumulated since last LoRA
- (b) Time-based — monthly or quarterly retrain regardless of corpus growth
- (c) Drift-triggered — retrain when D_KL(P‖Q) exceeds threshold
- (d) Other

**Answer:** _____
**Inferred Rule:** _____

---

### Q4.4 — `[MEDIUM]` Withhold-sampling selection policy

**Context.** ARCHITECTURE_PRINCIPLES.md mentions p_withhold = 0.05 as starting point. The *selection* policy — which events get withheld — isn't fully specified.

**Options:**
- (a) Uniform random across all events that trigger guidance
- (b) Stratified — ensure withhold coverage across primitive types proportional to advisory volume
- (c) Risk-weighted — never withhold on high-stakes events (crisis-tier), withhold preferentially on routine ones
- (d) Other

**Answer:** _____
**Inferred Rule:** _____

---

## §5 — LLM Integration

### Q5.1 — `[HIGH]` LLM choice for PIE elicitation vs Twin guidance

**Context.** `LlmClient` abstracts the provider, but the *use cases* differ. Elicitation is a structured-output task; guidance is a synthesis task.

**Options:**
- (a) Same provider for both — minimize complexity
- (b) Different providers per use case — Claude for guidance (long-context reasoning), Gemini for elicitation (cheaper structured-output)
- (c) Provider chosen per-deployment — facility config decides
- (d) Other

**Answer:** _____
**Inferred Rule:** _____

---

### Q5.2 — `[MEDIUM]` On-device vs cloud ASR

**Context.** SELECTION_PRINCIPLE.md §4.5 says on-device ASR for transcripts. Some Android devices have weak ASR; cloud Whisper would be more accurate.

**Options:**
- (a) On-device always — privacy-by-architecture is non-negotiable
- (b) On-device default, cloud opt-in for low-confidence transcripts
- (c) Operator-controlled toggle — they choose per-shift or per-event
- (d) Other

**Answer:** _____
**Inferred Rule:** _____

---

### Q5.3 — `[MEDIUM]` Vision API for instrument digitization

**Context.** Whitepaper v8 mentions Claude and Gemini Vision APIs for analog gauge reading. The choice between them affects cost and latency.

**Options:**
- (a) Single vendor by default; switch only if performance demands
- (b) Dual-call and use whichever returns first that crosses confidence threshold
- (c) Cascade — cheap model first, expensive model only on low-confidence
- (d) Other

**Answer:** _____
**Inferred Rule:** _____

---

## §6 — IP, Patent & Disclosure

### Q6.1 — `[ANSWERED 2026-05-24]` arXiv submission timing relative to non-provisional patent

**Context.** Provisional is filed. Non-provisional has a 12-month window. arXiv submission is gated on provisional for Paris Convention purposes. Where does it sit relative to non-provisional?

**Options:**
- (a) arXiv before non-provisional — establish academic priority date and let the non-provisional cite published prior art
- (b) Non-provisional before arXiv — let the patent counsel review the arXiv text against the patent claims first
- (c) Coordinated same-week — submit both within a tight window
- (d) Other

**Answer:** (a) arXiv before non-provisional. Non-provisional not yet filed as of 2026-05-24 — 12-month provisional window is active.
**Inferred Rule:** → SELECTION_PRINCIPLE.md §4.6

---

### Q6.2 — `[HIGH]` What's defensible as trade secret vs what gets published

**Context.** The patent covers PIE method. The whitepaper publishes CIAER schema. The codebook and corpus are clearly trade secret. The graph-weight computation method is borderline.

**Options:**
- (a) Publish anything that doesn't directly enable a competitor to build the system — methods yes, weights no
- (b) Publish nothing beyond what the patent requires for enablement — preserve maximum trade secret surface
- (c) Publish the architecture but redact the empirically tuned constants (θ_TC, τ, p_withhold) — methods are public, calibrations are trade secret
- (d) Other

**Answer:** _____
**Inferred Rule:** _____

---

### Q6.3 — `[MEDIUM]` Open-source posture for the provider abstraction layer

**Context.** The Kotlin interfaces (CaptureSource, BiometricSource, etc.) are scaffolding, not core IP. Open-sourcing them could accelerate community contributions to additional source implementations.

**Options:**
- (a) Open-source the interfaces under permissive license; keep implementations proprietary
- (b) Open-source the interfaces + one reference implementation per category to seed the community
- (c) All proprietary until the company has more leverage to set licensing terms
- (d) Other

**Answer:** _____
**Inferred Rule:** _____

---

## §7 — Deployment & Adoption

### Q7.1 — `[ANSWERED 2026-05-24]` Validation site beyond PPVC Line 1

**Context.** The sample-of-one problem at PPVC is acceptable because of architect honesty, but the next deployment needs an operator who isn't you. Which line?

**Options:**
- (a) Second extrusion line at Hollowell — same domain, different operator, controls for operator variance
- (b) Different domain (welding, machining) — tests CIAER+ domain-agnosticism
- (c) Same line, second shift — minimum-confound expansion
- (d) Other

**Answer:** (d) Three-step sprint within one week once recording begins: (1) same line same shift different operator, (2) primary operator on second extrusion line with different material and failure modes, (3) different operator on that second line.
**Inferred Rule:** → SELECTION_PRINCIPLE.md §4.3

---

### Q7.2 — `[HIGH]` Hollowell deployment agreement structure

**Context.** Currently blocked. The agreement needs to cover data rights, deployment cost, exclusivity, and exit terms.

**Options:**
- (a) Free pilot, full data rights to Capps Consulting, Hollowell retains derived-insights use rights
- (b) Paid deployment, shared data rights, exclusivity period for Hollowell in their region
- (c) Equity arrangement — Hollowell takes a stake in exchange for being the validation partner
- (d) Other

**Answer:** _____
**Inferred Rule:** _____

---

### Q7.3 — `[MEDIUM]` Operator opt-in mechanism

**Context.** When the system goes to a second operator at Hollowell, what's the consent structure?

**Options:**
- (a) Standard employment-context consent through Hollowell HR — operator informed but not given individual veto
- (b) Operator-individual signed consent, with operator owning their captured data — same as Personal AI Twin ownership rule
- (c) Tiered — Hollowell gets observation rights; the operator retains Twin ownership and can take it with them if they leave
- (d) Other

**Answer:** _____
**Inferred Rule:** _____

---

## §8 — Marketplace (Phase 5+)

### Q8.1 — `[LOW]` Marketplace launch site

**Context.** The marketplace addendum is comprehensive but doesn't specify *where* it launches first. Options range from operator-direct to platform-mediated.

**Options:**
- (a) Capps Consulting hosts the marketplace directly — full platform control
- (b) Partner with an existing industrial-tech marketplace (Augmentir-like) — speed at cost of control
- (c) Open protocol, no central marketplace — let any party host listings using ArcShield-spec Twins
- (d) Other

**Answer:** _____
**Inferred Rule:** _____

---

### Q8.2 — `[LOW]` Cross-domain Twin deployment policy

**Context.** A PVC extrusion Twin deployed to an injection molding facility is flagged experimental in the marketplace addendum. The rule for *upgrading* it to supported isn't given.

**Options:**
- (a) Requires N successful cross-domain deployments + a Super-Corpus pattern analysis confirming pattern transfer
- (b) Domain-pair certification — extrusion↔molding is certified after one validated bridge deployment, not all domain pairs simultaneously
- (c) Always experimental — cross-domain is permanently caveat-flagged; same-domain cross-facility is the supported mode
- (d) Other

**Answer:** _____
**Inferred Rule:** _____

---

## §9 — Documentation System

### Q9.1 — `[HIGH]` When does a file get added to the von Neumann set

**Context.** The current set is README + CLAUDE.md + CURRENT_PHASE.md + ARCHITECTURE_PRINCIPLES.md + SELECTION_PRINCIPLE.md. Future additions are likely.

**Options:**
- (a) Add a file only when an existing file would otherwise exceed its scope — strict minimalism
- (b) Add a file whenever a new *role* in the von Neumann triple needs externalizing — e.g., a future `OPERATOR_INTERFACE.md` for downstream consumers
- (c) Allow the set to grow organically; CLAUDE.md §0 always lists current set so re-entry stays bounded
- (d) Other

**Answer:** _____
**Inferred Rule:** _____

---

### Q9.2 — `[MEDIUM]` Archive policy for retired rules

**Context.** SELECTION_PRINCIPLE.md §0 says retired rules are kept with `[RETIRED YYYY-MM-DD]` tag. At what point do they move out of the live file?

**Options:**
- (a) Never move — retired rules stay in the live file forever as architectural memory
- (b) Move to `selection_principle/archive/` when retired count exceeds 20 — keep live file scannable
- (c) Move on a fixed schedule — annual archive sweep
- (d) Other

**Answer:** _____
**Inferred Rule:** _____

---

## §10 — Meta

### Q10.1 — `[HIGH]` What's *not* in this elicitation list that should be

**Context.** This file is itself a constructor output. It cannot fully cover its own gaps — the architect knows things the constructor cannot infer are missing.

**Options:**
- (a) Add specific question(s) here — write them in and answer them in the same pass
- (b) The list is complete enough to start; add as gaps emerge
- (c) Run a second elicitation pass after the first round of answers is in
- (d) Other

**Answer:** _____
**Inferred Rule:** _____

---

### Q10.2 — `[HIGH]` Tone and posture for SELECTION_PRINCIPLE.md when it's read by a non-Kahn architect

**Context.** This file will be read by a successor. The selection function is partly judgment, partly aesthetic. How should it be expressed?

**Options:**
- (a) Maximally explicit and impersonal — "the rule is X" — make it inheritable as cleanly as possible
- (b) Preserve voice — the file should read as if Kahn is in the room — first-person rationale where relevant
- (c) Hybrid — rules in third person, occasional first-person commentary marked as such
- (d) Other

**Answer:** _____
**Inferred Rule:** _____

---

## Answer Workflow

When you answer a batch:

1. Fill in the **Answer** and **Inferred Rule** fields above.
2. For each answered question, copy the Inferred Rule into the matching section of `SELECTION_PRINCIPLE.md` (§4 by domain or §5 if it's a rejection).
3. Mark the question `[ANSWERED YYYY-MM-DD]` in the heading line here. Don't delete — this is append-only.
4. If a question surfaces a deeper invariant, promote it to `ARCHITECTURE_PRINCIPLES.md` instead and leave a stub here pointing to where it went.
5. Commit all three files (this one, SELECTION_PRINCIPLE.md, and any others touched) in one commit. The DNA, the marker, and the new gene express together.

---

*End of ELICITATION_LOG.md. PIE method, applied inward. The architect performs the same elicitation they designed for operators — externalizing tacit rules into structured, transferable knowledge.*
