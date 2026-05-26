# SELECTION_PRINCIPLE.md

**This file externalizes the selection function — the decision rules and aesthetic judgments the architect uses to authorize mutations to the system. It is the fifth file in the von Neumann set (README + CLAUDE.md + CURRENT_PHASE.md + ARCHITECTURE_PRINCIPLES.md + this).**

Where CLAUDE.md says *what the system is*, ARCHITECTURE_PRINCIPLES.md says *why the system has the shape it has*, and CURRENT_PHASE.md says *where the build is right now* — this file says *how the architect would decide* about a question the other four don't directly answer.

Read this file when you face a design choice that isn't pre-decided in CLAUDE.md and isn't constrained by the invariants in ARCHITECTURE_PRINCIPLES.md. The rules below tell you which way the architect would lean.

> **Update protocol.** This file is append-mostly, like the session log. When a new decision is made, write down the rule that produced it. When a rule is promoted to a deep invariant, move it into ARCHITECTURE_PRINCIPLES.md or CLAUDE.md and leave a stub here pointing to where it went. Never delete a rule silently — if a rule is retired, mark it `[RETIRED YYYY-MM-DD]` with the replacement rule below it.

---

## 0. The Meta-Rule

> When the rule below is genuinely silent on a question, do not invent an answer. Park the decision in CURRENT_PHASE.md §6 (Open Decisions Awaiting Author Sign-off). The architect's role exists precisely because the artifact cannot complete the selection function on its own. Honor that.

---

## 1. Architectural Defaults

These are the rules that apply when the question is "should we do A or B?" and no other consideration is decisive.

### 1.1 Closed-form before learned
Closed-form, hand-curated, or rule-based implementations come first. Learned components are introduced only when the corpus is deep enough to train them honestly and only as additive, never replacement. The closed-form path remains available as a sanity check on the learned one.

**Seed example:** Phase 1 LLR gate uses Kay 1998 GLR statistics, not a learned classifier. Phase 1 codebook is a hand-curated Python dict, not RVQ.

### 1.2 Physical grounding over behavioral grounding
When a quantity could be derived from operator behavior or from physical floor outcomes, prefer the physical outcome. R_phys before compliance. Telemetry before self-report. Measurement before claim.

**Seed example:** OGC firewall — confidence updates only on R_phys, never on `[a_t = â_t]`. Compliance is a gate scalar, not a reward source.

### 1.3 Discard by default
The default action for any captured stream is to drop it. Persistence requires a positive reason — gate fire, validation flag, codebook miss. The system spends almost nothing on storage at rest.

**Seed example:** Λ < τ → ring buffer overwrites. Λ ≥ τ → window is muxed into the container.

### 1.4 Honest absence over silent interpolation
When data is missing, emit an explicit gap marker. Never smooth across discontinuities. The corpus must see what wasn't measured as clearly as what was.

**Seed example:** Polar BLE dropouts emit `BiometricGap(start, end, reason)` events into the timed-metadata track, not interpolated values.

### 1.5 Provider abstraction at every channel
Every hardware or external dependency is consumed through an interface. The interface is named for the *function*, not the *device*. Gen-to-Gen hardware swaps are DI binding changes, not structural rewrites.

**Seed example:** `BiometricSource` interface — `HealthConnectBiometricSource` (deprecated), `PolarBleBiometricSource` (current), `EmotibitBiometricSource` (deferred). Consumer code never references the device class.

### 1.6 Schema invariance across phases
The CIAER+ JSON schema does not change between Gen 1 and Gen 3 hardware. Every event captured today must be valid training data for systems running on Gen 3 hardware. If a proposed schema change would invalidate prior events, the schema change is wrong, not the prior events.

### 1.7 First-class structure over free-text notes
When information has type (rejected alternatives, gap reasons, escalation states, confidence levels), it becomes a structured field. Free-text notes are the fallback, never the primary storage. Voice transcripts are an exception: they are deliberately verbatim because the ASR-to-structure step happens downstream during PIE validation.

**Seed example:** Shadow Actions as `{action_type, rejection_rationale, confidence_in_rejection}`, not a string field.

---

## 2. When Two Valid Approaches Conflict

These are the tiebreakers when both options are defensible.

### 2.1 Defensibility before speed-to-market
A faster path that compromises the IP defensibility of the core claims is the wrong path. The patent and the corpus are the company's long-horizon assets; near-term velocity that erodes either is anti-strategic.

### 2.2 Adoption friction before margin
Pricing and deployment decisions optimize for adoption depth and corpus growth, not near-term unit economics. The Super-Corpus is the long-horizon asset; pricing that creates adoption friction is anti-strategic even if it improves near-term margin.

**Seed example:** Enterprise tier priced *below* Individual Pro because enterprise deployments produce the highest-quality corpus data.

### 2.3 Operator dignity before platform leverage
When a decision affects the relationship between the operator and their data (ownership, portability, certification, earning potential), default toward the operator. The Personal AI Twin belongs to the operator. Marketplace earnings split 70/30 in the operator's favor. Certification milestones are framed as formal acknowledgment of the operator's accumulated expertise.

### 2.4 Honesty over completeness
A spec section that flags a limitation honestly (cEDA not exposed, EDA permanently off Polar, Pixel Watch skin temp is sleep-only) is better than an omitted limitation or an aspirational claim presented as built capability. Aspirational features are marked ⚠ Speculative / Research-Dependent or moved to the Limitations section.

### 2.5 Reversibility before optimality
When a decision can be reversed cheaply later, prefer the cheaper-to-reverse option even if it is locally suboptimal. When a decision cannot be reversed cheaply (schema, IP, container format), spend more time getting it right up front.

**Seed example:** Phase 1 dict codebook is cheap to throw away when RVQ comes online. Container format choice (fMP4) is hard to reverse, so it gets full deliberation.

### 2.6 External grounding before internal consistency
A check against external reality (PLC telemetry, lab QC, author sign-off, peer review) beats a check against the system's own outputs. When the system grades itself, drift starts.

---

## 3. What Counts as a "Done" Decision

A decision is ready to commit when:

1. It satisfies all five invariants in ARCHITECTURE_PRINCIPLES.md §3 (D/A separation, grounded outcome, counterfactual preservation, append-only history, bounded mutation rate).
2. It has a named rule in this file that produced it. If no rule produced it, write the rule first.
3. The counterfactual — the option not chosen — is recorded somewhere (commit message, CURRENT_PHASE.md §6 archive, or as a `[REJECTED]` rule in this file with rationale).
4. There is an empirical or external signal that will eventually tell you whether the decision was right. If no such signal exists, the decision is provisional and should be flagged for re-evaluation.

---

## 4. Decision Logs by Domain

The categories below organize accumulated decisions by area. Each entry is a rule extracted from a past decision, written as a transferable principle a successor can apply to a new but related question.

### 4.1 Codec & Container

- **fMP4 over Matroska.** Standard tooling, mobile interop, free PTS handling. Chosen for Phase 1 because the cost of inventing a new container outweighs Matroska's marginal flexibility.
- **H.264 (AVC) for the POV track on Gen 1; H.265/HEVC at Gen 2.** [Q1.1, 2026-05-26] Codec choice tracks hardware-accelerated availability and decoder ubiquity at the *current* hardware tier, not peak compression. H.264's broad decoder support and low encode cost on phones win until Meta Ray-Ban hardware makes HEVC free. (Supersedes the earlier "H.265 for POV" note.) AAC for acoustic; `application/octet-stream` timed metadata for everything else — the container's track diversity is the architectural feature; per-track codec follows convention.
- **Sample rate is set per track by what that track's analyses need.** [Q1.2, 2026-05-26] Environmental acoustic stays 48 kHz (preserve >8 kHz spectral content for not-yet-specified analysis); voice-annotation audio is 16 kHz when captured as audio. Richness on the environmental channel, economy on the voice channel.
- **ε_sync target ≤ 100 ms, soft tolerance to 250 ms.** Above 250 ms a window is flagged `low_sync_confidence` and excluded from primitive matching but retained in the corpus. The asymmetry between matching-eligible and corpus-eligible is intentional — the corpus learns from imperfect captures, but the codebook is built only from clean ones.

### 4.2 Biometrics

- **Polar PMD over Health Connect.** Raw R-R intervals enable defensible nonlinear HRV claims. Sample-level PMD timestamps clean up ε_sync. The wrist-watch form factor was originally chosen for adoption friction, but the H10 / Verity Sense pair preserves wearability while delivering research-grade signal.
- **H10 and Verity Sense are both supported from day one with equal standing in code.** Auto-detection is the primary path — scan for paired Polar devices, identify by model, connect to whichever is present. If both are detected simultaneously, surface an operator selection prompt rather than silently prioritizing. No hard primary/secondary designation in the implementation; device priority is determined at runtime by what's detected, not by configuration. [Q2.1, 2026-05-24]
- **Multiple paired wearables → compose the best source per channel, not a primary device.** [Q2.2, 2026-05-26] When both H10 and Verity Sense are connected, route HR/ECG/R-R from the H10 (true ECG) and accel from the Verity Sense. The provider layer selects per-channel, not per-device — no hard primary/secondary designation. Generalizes: when several sources can serve a channel, bind each channel to its highest-fidelity available source.
- **EDA permanently off the wrist channel.** Companion sensor (Emotibit, Shimmer GSR+) behind the same `BiometricSource` interface when EDA matters. Don't design it out, but don't pretend it's coming back to the wrist.

### 4.3 Schema & Corpus

- **schema_version is REQUIRED on every event.** Forward-compatible parsing is non-negotiable; the corpus must survive schema evolution.
- **shadow_actions is REQUIRED at KNOWLEDGE-level events.** KNOWLEDGE without rejected alternatives is suspicious and gets flagged for re-elicitation. This rule generalizes: when an inference type implies deliberation, the deliberation residue must be captured.
- **model_revision is REQUIRED when hypothesis_confirmed = false.** Disconfirmed events without revisions are useless to the learning system. Failure events that update the operator's model are the highest-information records in the corpus.
- **Monte Carlo augmentation operates only on structured sensor-derived components, never on voice transcripts.** Voice content is never fabricated. This is the corpus-integrity firewall.
- **`operator_id` is a self-sovereign key generated and held by the operator.** Its sole function is provenance attestation — guaranteeing that events claiming to originate from this operator actually did. It carries zero system privileges. The integrity guarantee is one-directional: no one can forge source attribution on your events, but holding the key grants nothing beyond the ability to sign your own records. Portable across facilities and marketplace deployments from day one. [Q3.1, 2026-05-24]
- **Corpus validation expands in a three-step sprint.** Once event recording begins, the corpus moves from single-operator to multi-operator multi-domain within one week: (1) same line, same shift, different operator — isolates operator variance with all process variables held constant; (2) primary operator on a second extrusion line with different material and failure mode set — isolates domain variance; (3) different operator on that second line — cross-validates both simultaneously. Sprint model controls for seasonal and process drift that would confound a slow rollout. [Q7.1, 2026-05-24]
- **R_phys closure is shift-bounded, with a deductive in-shift fallback.** [Q3.3, 2026-05-26] An event cannot stay open past the operator's shift end — off-shift the operator can no longer contribute — so at shift end any event still lacking R_phys is marked `INDETERMINATE` and never updates C. Within the shift, R_phys resolves two ways: (1) telemetry / physical floor (preferred), or (2) deduction from the operator's subsequent decision stream — continued decision events on the same Cause ⇒ problem unresolved (R_phys not yet satisfied); cessation of related decisions ⇒ the physical state held (implicit positive R_phys). Telemetry takes precedence; deductive R_phys is the in-shift fallback and is a lower-confidence, behavior-derived *estimate of physical state* — it must stay independent of the compliance gate `[a_t = â_t]` (CLAUDE.md §6.3): it reads whether the operator is still working the problem, never whether they complied with advice.

### 4.4 Codebook & Learning

- **HITL validation gates every codebook expansion.** A novel waveform doesn't become a primitive until a human has structured it into CIAER+ and named the failure mode. This is the bounded-mutation-rate invariant at product scale.
- **RAG before LoRA.** Phase 2 advisory uses retrieval; LoRA arrives only at ~200 events. Premature LoRA on a thin corpus produces a confidently wrong Twin, which is worse than a retrieval system that surfaces the right historical event with no synthesis.
- **Withhold sampling instrumented from day one, enabled only when advisory is live.** The pipeline exists in Phase 1 with p_withhold = 0. Turning it on is a config change, not a code change. Counterfactual evidence collection cannot be retrofitted; the data path must exist before the data does.
- **θ_TC is a per-primitive field in the codebook schema from day one, initialized to a single globally-calibrated value.** The global value is set empirically during Phase 1 shadow-mode from hand-labeled candidates targeting ~80% TP rate, then locked. Per-primitive divergence from the global value is a Phase 3+ data change when observation depth per primitive is sufficient to estimate variance honestly. Adaptive auto-tuning is rejected until Phase 3+ and only if the global+per-primitive path proves insufficient. [Q4.1, 2026-05-24]

### 4.5 LLM Integration

- **LlmClient abstracts the provider.** Claude, Gemini, future local models all sit behind the same interface. Provider choice is a deployment parameter, not an architectural commitment.
- **On-device ASR for voice transcripts.** Privacy-by-architecture for the operator's words. Cloud LLM calls are for elicitation and guidance synthesis, not raw transcription.

### 4.6 IP & Documentation

- **mp4Real™, ArcShield™, CIAER™, CIAER+™ trademark notice on every public artifact.** Non-negotiable, exact text in CLAUDE.md §15.
- **Provisional patent before arXiv, arXiv before non-provisional.** The sequence is: provisional → arXiv → non-provisional. The provisional establishes the Paris Convention priority date; arXiv establishes the academic priority date; the non-provisional cites both. Counsel reviews the arXiv text against the claims before non-provisional drafting, not before arXiv submission. [Q6.1, 2026-05-24]
- **Specification rigor over marketing.** Domain-neutral technical language. Speculative elements explicitly flagged. Aspirational features never presented as built features.

### 4.7 Deployment & Adoption

- **PPPVC Line 1 first, full deployment second.** Validate the foundational pipeline on one expert at one site before generalizing. The validation site is the author. This is acceptable because the architect is honest about confounds and adversarially stress-tests their own claims; it would not be acceptable for an outside observer studying a sample of one.
- **Zero-IT-footprint single-file PWA for Gen 1 proof of concept.** No enterprise IT involvement required; no capex required; no operator adoption friction. Validates that the pipeline works before investing in dedicated hardware.

### 4.8 Documentation System

- **CLAUDE.md is the genome and changes only with author sign-off.** Drift here is the highest-cost form of drift.
- **CURRENT_PHASE.md is the phenotype and updates every session.** Drift here is recoverable.
- **Session log is append-only.** Truncate §5 by archiving; never delete from §8.
- **Decisions get parked in §6 if the rule below doesn't cover them.** The constructor cannot perform the selection function on itself. Honor the separation.

---

## 5. Rejected Approaches (the "Shadow Actions" of architecture)

When a rule was considered and rejected, document it here. This is the architectural equivalent of the Shadow Actions field — the negative space of the selection function.

- **[REJECTED] Auto-update CLAUDE.md when Claude Code detects a recurring pattern.** Collapses D and A at meta scale. Fails Invariant 1. See ARCHITECTURE_PRINCIPLES.md §4 worked example.
- **[REJECTED] Use Wear OS Activity Recognition on the wrist for activity classification.** Polar Verity Sense + phone accel covers it; the Wear-OS path lost when the wearable platform shifted to Polar. Activity classification now runs phone-side from phone accel optionally fused with Polar's accel stream.
- **[REJECTED] Health Connect as biometric SDK.** Replaced by Polar PMD. cEDA was never exposed on Pixel Watch anyway; raw R-R is the gain. The Health Connect provider abstraction layer is preserved as a fallback interface in case a future device pairs through it.
- **[REJECTED] Learned LLR gate in Phase 1.** No corpus to train on, no way to validate, premature learning. Phase 3+ upgrade path documented.
- **[REJECTED] Continuous voice recording.** Architectural privacy commitment: voice capture fires only during explicit PIE prompt windows or operator-initiated triggers. The transcript track is therefore sparse, not dense.

---

## 6. Pending Elicitation — Author Sign-off Required

The following questions are decisions the author has likely made implicitly but has not yet externalized in writing. Until they are answered here, they live in CURRENT_PHASE.md §6.

The answers go in `phases/ELICITATION_LOG.md` (companion file). When an answer is given there, the corresponding rule is added to this file in the relevant §4 subsection.

---

## 7. Notes on Self-Application

This file is itself subject to the rules it contains. A change to a rule here is treated like a CLAUDE.md change: it requires author sign-off, the prior rule is preserved as `[RETIRED YYYY-MM-DD]` rather than deleted, and the rationale for the change is logged in the session log.

The selection function evolves. But it evolves under the same constraints it applies to everything else.

---

*End of SELECTION_PRINCIPLE.md. The architect's role made writeable. Future instances inherit not just decisions but the rules that produced them.*
