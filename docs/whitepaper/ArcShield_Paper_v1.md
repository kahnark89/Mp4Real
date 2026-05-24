# ArcShield: A General Protocol for Transmissible Expertise

## Genomic Architecture, Outcome-Grounded Confidence, and the Externalization of Tacit Cognition Under Physical Constraint

**Kahn Capps**
Capps Consulting Company LLC, Helena-West Helena, Arkansas
kahn.capps@cappsconsulting.example

**Date:** May 2026
**Version:** 1.0

---

## Abstract

We describe ArcShield, a cyber-physical system originally designed to capture expert decision-making from veteran industrial operators and convert it into structured data suitable for training adaptive AI agents. In the course of its development, the system has acquired structural properties that exceed its original scope. We argue that ArcShield instantiates a general protocol for *transmissible expertise* — a method for converting any cognitive agent's tacit knowledge into inheritable structure, with architectural defenses against the failure modes (causal confusion, reflexive self-confirmation, distributional drift, loss of counterfactual evidence) that have historically prevented machine learning systems from acquiring genuine expert judgment from observational data. The system exhibits five structural properties that we identify as load-bearing: (1) a domain-agnostic five-phase decision schema (CIAER+), (2) a multimodal rate-distortion gate (the mp4Real™ codec) that discards the overwhelming majority of captured signal under information-theoretic criteria, (3) an outcome-grounded confidence update rule (OGC) that physically separates the reward signal from operator compliance, (4) a withhold-sampling subsystem that maintains a counterfactual distribution at deployment scale to detect reflexive drift, and (5) a documentation architecture that applies the same capture protocol to its own architect, producing a self-extending system. We show that this five-property pattern is structurally isomorphic to von Neumann's universal-constructor triple (Burks 1966), to the genotype-phenotype-epigenome architecture of biological inheritance (Waddington 1957; Pigliucci 2010), and to recent layered-memory architectures for large language model agents (Fofadiya & Tiwari 2026; MemMachine 2026). We argue that the convergence of these three lineages on the same structural pattern is not coincidence but a consequence of the constraints imposed by the problem of preserving information through replication. We close by considering the implications for industrial AI, for the design of self-extending socio-technical systems, and for the responsibilities of architects who occupy what we describe as the *selective principle* role — structurally analogous to natural selection in biological systems but instantiated consciously and in compressed time.

**Keywords:** tacit knowledge, expert systems, imitation learning, causal confusion, behavioral cloning, somatic markers, predictive processing, knowledge graphs, self-replicating systems, universal constructor, epigenetic inheritance, genotype-phenotype mapping, large language model memory, industrial AI, manufacturing.

---

## 1. Introduction

### 1.1 The Problem

Industrial expertise is dying faster than it is being transmitted. Across manufacturing, surgery, aviation maintenance, precision agriculture, and other domains where embodied judgment under uncertainty determines outcome quality, the demographic transition is producing a structural shortfall: veteran practitioners are retiring, and the apprenticeship pathways that historically transmitted their tacit knowledge are insufficient at the scale and pace the transition demands. This is not a training problem. It is a *capture* problem. The knowledge that needs to move from veteran practitioners to their replacements is largely unavailable to the veteran practitioners themselves in articulable form. It exists as tacit, embodied, multimodal expertise — the kind Polanyi (1966) characterized as "we know more than we can tell."

Connected-worker software platforms and AI-assisted training systems have proliferated in response to this demographic pressure (Augmentir 2024; Tulip 2024; LandingAI 2024). They have not solved the capture problem. We argue in §2 that they cannot solve it as currently architected, because they treat the capture problem as fundamentally a *procedure documentation* problem — what the expert *does* — rather than a *causal decision* problem — what the expert *responds to, infers, projects, considers, rejects, and updates on*.

The distinction is not pedantic. Behavioral cloning systems trained on procedure documentation suffer from causal confusion (de Haan, Jayaraman, & Levine 2019): they learn correlations between actions and contextual features without access to the causal variables the expert conditioned on. Under distributional shift, they fail in characteristic ways — failures that no amount of additional procedure documentation can fix, because the missing information was never in the procedure documentation to begin with.

ArcShield is a system designed to make the causal variables capturable. The system was developed over 2025–2026 at PPVC Line 1, Hollowell Industries, a PVC extrusion facility in Helena-West Helena, Arkansas. The architect is a working extrusion operator at the same facility, which produces both a methodological advantage (the system is being designed by the population whose expertise it captures) and a constraint requiring careful handling (the validation set is a sample of one until further deployment, with all the limitations that implies; we treat this explicitly in §7).

### 1.2 The Move That Changed the System

Through development, the system acquired a property we did not initially design for and which we believe is the most consequential property of the work. The same protocol the system uses to capture an industrial operator's tacit expertise can be — and now is — applied to the system's own architect. The architect's selection function (the rules that determine which design alternatives survive into the codebase) is being externalized into structured, transferable artifacts using the same elicitation protocol the system applies to operators. The system has become recursive: it captures the cognitive agent that designed it.

This property has consequences we did not anticipate. It produces a self-extending system, in a sense we make precise in §6. It changes the relationship between the architect and the artifact in ways that have structural parallels in the von Neumann formalization of self-replicating automata (Burks 1966), in evolutionary biology's genotype-phenotype mapping framework (Pigliucci 2010), and in recent layered-memory architectures for large language model agents (Fofadiya & Tiwari 2026). The fact that these three lineages — formal logic of self-replication, biology of inheritance, and engineering of AI memory — converge on the same structural pattern when forced to solve the same underlying problem (preserving information through replication under reflexivity pressure) is, we argue, a significant finding in its own right.

### 1.3 Contributions

This paper makes four contributions:

1. **A schema (CIAER+)** for representing expert decision events as machine-readable structure, designed explicitly to avoid causal confusion by capturing the variables behavioral cloning systems systematically miss: shadow actions (rejected alternatives), pre-cause environmental layer, and outcome-grounded model revisions on disconfirmed hypotheses (§3).

2. **A codec architecture (mp4Real™)** that treats multimodal industrial decision capture as a rate-distortion problem under the Minimum Description Length principle (Rissanen 1978), implementing a log-likelihood ratio gate that drops the overwhelming majority of captured signal and stores only the high-information windows that warrant compression against a behavioral codebook (§4).

3. **An outcome-grounded confidence update rule (OGC)** that architecturally separates the physical reward signal from operator compliance, preventing the reflexive self-confirmation pathology that has been documented in deployed reinforcement learning systems (Amodei et al. 2016; Krakovna et al. 2020). The OGC firewall is enforced at the database schema level via foreign-key constraints, not as a code discipline (§5).

4. **A documentation architecture** that applies the capture protocol to the system's own architect, producing a five-file structure — README, CLAUDE.md, CURRENT_PHASE.md, ARCHITECTURE_PRINCIPLES.md, SELECTION_PRINCIPLE.md, plus an append-only elicitation log — that we show is structurally isomorphic to the von Neumann triple and to the genotype-phenotype-epigenome architecture of biological inheritance (§6).

We also argue, more speculatively, that the convergence of these four contributions produces a fifth property we did not initially set out to achieve: a general protocol for transmissible expertise that is substrate-independent, domain-agnostic, and self-extending. We discuss the implications and the responsibilities this places on architects of such systems in §8.

### 1.4 Roadmap

§2 reviews the literature on tacit knowledge, imitation learning failure modes, and connected-worker platforms, establishing why prior approaches have not solved the capture problem. §3 specifies the CIAER+ schema. §4 specifies the mp4Real codec. §5 specifies the OGC firewall and the withhold-sampling subsystem. §6 specifies the documentation architecture and argues for its structural isomorphism with biological inheritance and von Neumann self-replication. §7 reports the proof-of-concept deployment at PPVC Line 1 and discusses limitations. §8 discusses implications for industrial AI, for the design of self-extending systems, and for the architect's role. §9 concludes.

---

## 2. Background: Why Prior Approaches Cannot Solve the Capture Problem

### 2.1 The Causal Confusion Problem in Imitation Learning

Imitation learning trains a model to reproduce expert behavior by observing demonstrations. The model learns a mapping from observed states to expert actions. Under in-distribution conditions, the mapping reproduces the expert's behavior with high fidelity. Under distributional shift, the mapping fails — sometimes catastrophically.

de Haan, Jayaraman, and Levine (2019) characterized the central pathology as *causal confusion*. The model has access to the expert's actions and to the contextual features present when those actions were taken, but it does not have access to *what the expert was responding to* or *why the expert chose that action over the alternatives available*. It cannot distinguish causally relevant features from spuriously correlated ones. Under conditions where the spurious correlations break, the model fails in characteristic ways: it continues to predict the expert action despite the causally relevant variables having changed.

This is not a model architecture problem. It is a data structure problem. The training data does not contain the information the model would need to avoid causal confusion. No amount of additional behavioral demonstrations, no scaling of model parameters, and no improvement in the learning algorithm can recover information that was never recorded. A system that learns from procedure documentation is structurally incapable of acquiring genuine causal expertise from that documentation.

### 2.2 What a Complete Training Record Requires

For an AI system to acquire genuine expert judgment — rather than to mimic surface behavior under in-distribution conditions — its training data needs to contain five elements for every decision event. It needs the cause that triggered the decision: the specific environmental signal, instrument reading, or sensory anomaly that engaged the expert's attention. It needs the expert's causal model at the moment of decision: what they believed was happening, what they projected would occur without intervention, and why they selected the intervention they did over alternatives. It needs the action taken — and, crucially, the alternatives considered and consciously rejected. It needs the environmental response the action produced. And it needs whether the outcome confirmed the expert's causal model or required updating it.

These five elements — Cause, Intuition, Action, Effect, Result — together form the CIAER schema. Each element alone is insufficient. Actions without Cause and Intuition produce causal confusion. Causes without Actions and Effects produce sensor logs with no behavioral signal. Outcomes without the reasoning that predicted them cannot update a model. The five elements together close a causal chain that provides what behavioral cloning systems systematically lack: not just *what* the expert did, but *why*, *under what conditions*, and *with what model-updating consequence*.

We extend the original CIAER specification with two elements not present in the foundational schema. The Pre-ENV (pre-cause environmental) layer captures ambient baseline conditions — shift conditions, material batch, recent process history, crew state — that contextualize every subsequent event and serve as the I-frame against which subsequent deltas are computed. The Shadow Actions field captures rejected alternatives as first-class structured siblings to the action taken: a behavioral cloning system that sees only chosen actions has no signal on what was considered and rejected; SHADOW_ACTIONS supplies that signal. The extended schema is CIAER+.

### 2.3 What Connected-Worker Platforms Capture and What They Miss

Public documentation of the major connected-worker platforms (Augmentir, Tulip, Parsable, ServiceMax, and others, as of early 2026) describes capabilities organized around procedure documentation, work instruction delivery, compliance tracking, skills management, and (in some cases) AI-assisted analytics over these data. These platforms capture what experts do. They do not, by public documentation, capture what experts perceive, infer, consider, reject, or update on.

This is not an accident of implementation. It is a consequence of the *frame* the platforms inherit from their parent category. They are connected-worker platforms — productized from the historically dominant manufacturing IT category whose central artifact is the standard operating procedure. An architecture built on the procedure-documentation frame inherits the pathology of behavioral cloning by construction, because a procedural log is exactly the input that behavioral cloning consumes and on which it confuses correlates with causes. An architecture built on a different frame — what we call the bidirectional reconciliation frame, capturing the causal variables the expert conditioned on — can, in principle, avoid this pathology. Whether the theoretical advantage translates into a measurable downstream-model quality advantage is an empirical question that the proof-of-concept deployment at PPVC Line 1 is designed to begin resolving.

### 2.4 What Thought Cloning Identified as the Missing Substrate

Hu and Clune (2023), in their NeurIPS paper on Thought Cloning, identified the bottleneck preventing their architecture from demonstrating its full potential as the absence of a sufficient corpus of "humans thinking out loud while acting" — verbatim multimodal records of expert decision processes in real-time embodied contexts. The CIAER+ corpus is intended to supply exactly this substrate. The mp4Real codec is the mechanism by which such a corpus can be produced at scale on a live industrial floor without operator-adoption friction.

### 2.5 Position of This Work

We do not claim that CIAER+ is the only schema that could solve the capture problem, nor that the mp4Real codec is the only architecture that could implement causal-decision capture at industrial scale. We claim that CIAER+ and mp4Real together constitute a coherent, deployable system that addresses the causal confusion failure mode identified by de Haan et al. and the substrate gap identified by Hu and Clune, in a way that no public connected-worker platform addresses, and that this addressing is not a feature added to a prior architecture but a consequence of the architectural commitments at the level of schema and codec.

---

## 3. The CIAER+ Schema

### 3.1 Schema Overview

A CIAER+ event record is a JSON object representing one complete expert decision cycle from perceptual trigger through outcome evaluation. The top-level structure contains seven sections: an envelope (identity, provenance, graph linking), a pre-cause environmental layer, the five-phase decision chain (Cause, Intuition, Action, Effect, Result), and the Shadow Actions field. Each section contains a defined set of fields with explicit cardinality, type, and status (REQUIRED, OPTIONAL, or DERIVED).

The five-phase decision chain is formally a directed cyclic graph. The outward arc runs Cause → Intuition → Action → Effect → Result. The inward arc runs Result → Model Update → Cause (next cycle). The interval between cycles varies from milliseconds in reflexive skill-based responses to hours in complex knowledge-based deliberation. The structural architecture is invariant across this range, which is the testable core of the CIAER hypothesis.

### 3.2 Field Status Conventions

Three field-status conventions are used. REQUIRED fields must be present; absence breaks the causal chain and invalidates the record for ML training. OPTIONAL fields enrich the record when available; null is valid and handled by training pipelines. DERIVED fields are system-computed (e.g., delta computations on Effect, escalation_delta, graph_weight); they are never entered manually and are always present in complete records.

### 3.3 Phase Specifications

The ENVELOPE contains schema_version, event_id (UUID v4), timestamp_start, operator_id, facility_id, line_id, parent_event_id (for chains), and parallel_event_ids (for multi-expert divergence pairs). The schema_version field is REQUIRED on every event to enable forward-compatible parsing as the schema evolves.

The PRE_ENV section captures shift_phase, material_batch_id, ambient_temp_f, recent_events_summary, and crew_state_tag. This is the I-frame: shift-start baseline against which all subsequent deltas are computed.

The CAUSE section captures capture_timestamp, trigger_source (TIER_1_ACCELEROMETER | TIER_2_GAZE_DWELL | TIER_3_BIOMETRIC | OPERATOR_INITIATED — typically multi-tier), sensor_readings (array of `{instrument_id, value, unit, confidence}`), biometric_snapshot (HR, HRV-RMSSD, nonlinear HRV features including SD1/SD2 and sample entropy, accelerometer-gated activity class), acoustic_profile (spectral delta from baseline), gaze_dwell_duration_sec, and visual_anchor_description.

The INTUITION section captures srk_level (SKILL | RULE | KNOWLEDGE, per Rasmussen 1983), causal_hypothesis (free text), failure_mode_tag (controlled vocabulary), confidence_level (0–1), projection (what the expert anticipated would happen without intervention), voice_transcript (verbatim ASR output, never paraphrased or fabricated), and biometric_signature (delta vector at the moment of inference).

The ACTION section captures action_type (enum), action_timestamp, action_rationale, and action_sequence (ordered array of step-level records with parameter changes, from/to values, and per-step rationale).

The SHADOW_ACTIONS field is an array of `{action_type, rejection_rationale, confidence_in_rejection}` records. This field is REQUIRED at KNOWLEDGE-level events. KNOWLEDGE without rejected alternatives is suspicious and is flagged for re-elicitation: a deliberate decision at the highest cognitive level that involved no consideration of alternatives is either incompletely captured or is not actually KNOWLEDGE-level.

The EFFECT section captures capture_timestamp, sensor_readings, deltas (DERIVED — per-instrument `{delta, direction: IMPROVED|DEGRADED|UNCHANGED}`), and prediction_match (CONFIRMED | PARTIAL | DISCONFIRMED | INDETERMINATE).

The RESULT section captures completed_at, outcome_tag (PROBLEM_PREVENTED | PROBLEM_RESOLVED | PARTIAL_RESOLUTION | NO_CHANGE | WORSENED | TOO_EARLY), escalation_state_at_result, escalation_delta (DERIVED), hypothesis_confirmed (boolean), model_revision (OPTIONAL — REQUIRED when hypothesis_confirmed is false; high-value learning input), product_quality_impact, and graph_weight (DERIVED 0–1).

### 3.4 Schema Invariants Enforced at Write Time

Three invariants are enforced at write time. An event record with prediction_match = INDETERMINATE and no pending_R_phys deadline is rejected: every event must either close on its own telemetry or be queued for deferred outcome arrival. shadow_actions may be empty for SKILL-level events but is REQUIRED at KNOWLEDGE-level events, as above. model_revision is REQUIRED when hypothesis_confirmed is false: disconfirmed events without revisions are useless to the learning system, while disconfirmed events *with* revisions are the highest-information records in the corpus.

### 3.5 Domain-Agnostic Structural Invariance

The CIAER+ schema is structurally invariant across domains. A surgical team reading tissue texture, a pilot reading an attitude indicator, a welder reading puddle behavior, and an extrusion operator reading a pressure transducer all execute the same five-phase cycle. The sensor vocabulary and action vocabulary change by domain. The data structure is constant. We document the stress-test lineage of this claim in §3.6 below.

### 3.6 Adversarial Stress-Testing of the Domain-Agnostic Claim

A schema that claims domain-agnosticism must survive counterexamples. We applied an adversarial stress-test to the CIAER framework with nine named counterexamples, each chosen to potentially falsify the structural invariance claim. The Venus flytrap (passive trigger but no inference) drove topological phase individuation. *E. coli* chemotaxis (genuine four-phase agency with computationally minimal Intuition) drove the distinction between topological presence of a phase and computational sophistication within it. The bimetallic-strip thermostat (negative feedback control without model update) drove the explicit experience-dependent update requirement to the scope criterion. The patellar reflex (Dewey's founding example) drove multi-timescale tolerance: phases need not operate on a single clock. The rolling rock (unfalsifiability probe) demonstrated that the framework's discriminative content cannot come from the phases themselves but must come from operational criteria distinguishing CIAER agents from non-CIAER physical processes. The chess grandmaster (skilled but non-embodied) and the sterile expert (knowledgeable but inactive) drove the functional-plus-Millikan-grounding embodied-agent criterion. The AI system trained on expert data drove the explicit recursion criterion: a learning system that processes CIAER events is not itself a CIAER agent unless it embodies the schema operationally. The fast-food shift manager (abstract ratio-dashboard expertise without continuous physical embodiment) drove the recognition that CIAER's structural invariance extends across embodied vs. non-embodied agents and across physical industrial processes vs. abstract dashboard-based judgment. This nine-counterexample lineage is preserved in the system's documentation as a methodological commitment: structural claims face counterexamples before they enter the spec, not after.

---

## 4. The mp4Real™ Codec

### 4.1 Architectural Framing

The mp4Real™ codec is the runtime layer that produces CIAER+ events from continuous multimodal sensor streams. We frame it explicitly as a *codec* — not as a logger, not as a database front-end — because the framing dictates the architectural commitments. A video codec is a rate-distortion gate that admits high-information windows into a structured container and discards the rest under the Minimum Description Length principle (Rissanen 1978). A logger admits everything and filters later. The two architectures look superficially similar and behave very differently under industrial-scale deployment, where bandwidth, storage, and analyst attention are all bounded.

The default action of the mp4Real codec, for any captured stream, is to discard. Persistence requires a positive reason: a log-likelihood ratio gate has fired, indicating a high-information window worth muxing into the container. The architecture is described in detail below; the framing point is that the system burns power on capture but spends almost nothing on storage at rest. Most of reality is not worth encoding.

### 4.2 Container Format

The mp4Real container is implemented as fragmented MP4 (fMP4) with timed-metadata tracks under the ISO BMFF specification. We chose fMP4 over Matroska because the cost of inventing a new container outweighed Matroska's marginal flexibility advantages, and because fMP4's standard tooling (FFmpeg, MP4Parser, ExoPlayer) and mobile interoperability buy free Presentation Timestamp (PTS) handling and downstream consumer compatibility.

The container has seven tracks: POV video (H.265, hardware-encoded, 30 fps at 1080p), acoustic (AAC at 48 kHz), vibration / accelerometer (`application/octet-stream` timed metadata at approximately 100 Hz hardware ceiling), biometric (`application/octet-stream` timed metadata from Polar PMD-protocol wearables: raw ECG at 130 Hz on the H10 with derived R-R intervals; HR and accelerometer on the Verity Sense), thermal (variable rate; Open-Meteo proxy in Gen 1, Bluetooth IR thermometer path in Gen 2), voice annotation (`application/octet-stream` timed metadata with on-device ASR transcript and word-level timestamps; event-driven, never continuous), and process telemetry (`application/octet-stream` timed metadata from facility PLC in Gen 2+).

### 4.3 Synchronization Tolerance

We define ε_sync as the synchronization tolerance across multiplexed tracks under shared Presentation Timestamp. ε_sync ≤ 100 ms is the operational target; ε_sync ≤ 250 ms is the soft tolerance for primitive matching; ε_sync > 250 ms invalidates the window for codebook matching but preserves it in the corpus with a `low_sync_confidence` flag. The asymmetry is deliberate: the corpus learns from imperfect captures, but the codebook is built only from clean ones.

Clock anchoring on the phone side uses `elapsedRealtimeNanos()` (monotonic, immune to wall-clock adjustments). Polar PMD packets carry sample-level timestamps; we anchor to PMD sample-index timestamps at first packet receipt and advance by sample index thereafter. This was a meaningful improvement over the prior Pixel Watch 4 / Wear OS / Health Connect biometric path, which suffered from approximately 50–200 ms drift over a typical shift and required NTP-style handshake correction every five minutes.

BLE link stability around 480V motors and induction heaters is non-trivial. The Polar H10 holds approximately 30 minutes of data internally on link loss and supports back-fill sync on reconnect. The biometric provider source implementation buffers aggressively on the phone side, bakes reconnect logic into the source, and emits explicit `BiometricGap(start, end, reason)` events into the timed-metadata track on dropout. We never silently interpolate across a dropout: the corpus must see the gap as clearly as it sees the signal.

### 4.4 I-Frame and P-Frame Semantics

The I-frame is the 60–120 second baseline recorded at shift start, written as a sample at PTS=0 across all tracks. The LLR gate's null hypothesis H₀ is parameterized from this baseline. The I-frame is PRE_ENV made manifest at the container level.

The P-frame is the [t − W_pre, t + W_post] window emitted when the LLR gate fires. Default values are W_pre = 30 seconds and W_post = 60 seconds. The asymmetry reflects the structure of operator decisions: pre-window memory is bounded by ring-buffer cost; post-window length is bounded only by future events, and decisions typically resolve on a longer timescale than they trigger.

### 4.5 The Log-Likelihood Ratio Gate

The gate decides which captured windows are worth muxing. In Phase 1, we use closed-form streaming statistics rather than a learned classifier; we have no corpus to train a learned gate on, and a learned gate trained on a thin corpus would produce confidently wrong gating decisions worse than principled closed-form gating.

We compute two independent log-likelihood components. Λ_env (environmental) sums contributions from acoustic spectral KL divergence vs. baseline spectrum, accelerometer RMS over a rolling 5-second window, motion energy (frame-to-frame differencing on the POV track), and gaze dwell duration on a single anchor (sustained-attention proxy). Λ_bio (biometric) sums contributions from HR delta vs. 5-minute rolling baseline, HRV-RMSSD ratio vs. 5-minute baseline, nonlinear HRV deviation (SD1/SD2 and sample entropy) vs. shift-start baseline, and accelerometer-gated activity class (resting / light / moderate / vigorous, with high activity gating down Λ_bio because cardiovascular signal during physical work is confounded by exertion).

Under the conditional independence assumption S_env ⊥ S_bio | E, Z (where Z is nuisance variables including ambient heat from a 32:1 L/D barrel), the components sum: Λ = Λ_env + Λ_bio. The gate fires when Λ ≥ τ. We use the Kay (1998) generalized likelihood ratio approximation for the composite-hypothesis case. Threshold τ is calibrated empirically during a two-week shadow-mode run that logs candidate windows without persisting full containers; the resulting hand-labeled true-positive / false-positive distribution determines the τ value that achieves the target operational point (approximately 80% TPR / ≤ 20% FPR).

In Phase 3+, the closed-form gate will be augmented by (not replaced by) a learned classifier trained on the accumulated CIAER+ corpus. The closed-form gate remains available as a sanity check on the learned gate's drift. Replacement, not augmentation, would lose the diagnostic capability of an independent reference.

### 4.6 The Behavioral Codebook

When the gate fires, the captured window is muxed into the container and queued for backend processing. The backend demuxer splits tracks; per-track encoders produce fixed-dimensional embeddings; the codebook matcher computes the conditional total correlation TC(T₁, ..., T_K | π_i) for every primitive π_i in the codebook. Total correlation measures multi-information across the K tracks conditional on a primitive: high TC indicates this event looks like a known causal pattern; low TC indicates it does not.

The system selects π* = argmax_i TC(T₁, ..., T_K | π_i). If TC* ≥ θ_TC, the codec emits a compressed primitive-reference token: the index of π* plus a small Δ-deviation vector capturing how this instance differs from the canonical primitive. Storage cost is small because the primitive is in the codebook on both ends; only the deviation travels. If TC* < θ_TC, the codec stores the full uncompressed multi-track waveform and queues the event for human-in-the-loop validation. If validation confirms a coherent CIAER+ structure that does not match any existing primitive, the codebook expands: π_{n+1} is added.

This is Minimum Description Length operating directly. Novel events cost bits. Familiar events cost references. The corpus grows in two ways: by adding deviation vectors against existing primitives, and by expanding the codebook when genuinely novel patterns appear.

For Phase 1, with corpus depth below approximately 100 events, the codebook is a hand-curated Python dictionary keyed by failure_mode_tag, with matching done by cosine similarity over per-track summary embeddings. This is debuggable, inspectable, and avoids the trap of premature residual vector quantization on a corpus too thin to support it. For Phase 3+, with corpus depth above approximately 200 events, the codebook transitions to a learned residual vector quantization architecture with per-track causal Transformer encoders, joint contrastive training, and approximately three stages of 256 codes each.

---

## 5. Outcome-Grounded Confidence and the Withhold-Sampling Subsystem

### 5.1 The Reflexivity Problem

A pathology emerges once any AI system trained on observational data begins influencing the behavior it observes. The system's confidence in its predictions grows when operators comply with its guidance, but operator compliance is not evidence that the guidance was correct. It is only evidence that the operator complied. A naive confidence update rule, applied without distinction between compliance and outcome, will inflate the system's confidence in its current predictions regardless of whether those predictions are physically grounded. This is the reflexivity trap, structurally identical to the feedback-loop pathology documented in algorithmic prediction systems from recommender systems to predictive policing (Ensign et al. 2018; O'Neil 2016), and a known failure mode of reinforcement learning policies deployed in environments they are also shaping (Krakovna et al. 2020).

### 5.2 The Outcome-Grounded Confidence Update Rule

The Outcome-Grounded Confidence (OGC) update rule prevents the reflexivity trap by physically separating the reward signal from operator compliance. The formal update is:

```
δ = R_phys(T_{t+Δt}) − C_t(â_t | s_t)
C_{t+1}(â_t | s_t) = C_t(â_t | s_t) + α · δ · [a_t = â_t]
```

Where R_phys(T_{t+Δt}) is a continuous or binary scalar derived from the multiplexed process telemetry track T, measuring whether the physical state transitioned to spec independent of operator policy. C_t(â_t | s_t) is the system's prior confidence in the advised action â_t given state s_t. [a_t = â_t] is the compliance indicator: a *gating scalar* indicating whether the operator's observed action matched the advised action. α is the learning rate. δ is the temporal-difference error.

The critical structural property is that [a_t = â_t] is a gating scalar, never a reward source. The only quantity that updates confidence is R_phys. An operator who complies perfectly with bad advice cannot inflate that advice's confidence; an operator who deviates from good advice but reaches the right physical state still rewards the model.

### 5.3 Architectural Enforcement

The OGC rule is enforced at the database schema level via foreign-key constraints, not as a code discipline. Every event record carries a `pending_R_phys` field that holds the confidence update in suspension until the process telemetry post-window arrives. Only when R_phys is computed does the TD error δ apply; the compliance indicator enters only as a gating scalar at update time. Any code path that mutates C without an R_phys arrival is architecturally impossible.

The conditional independence guarantee is:

```
R_phys(T_{t+Δt}) ⊥⊥ [a_t = â_t] | s_{t→t+Δt}
```

No information path is permitted in the implementation that would allow operator compliance to be evidence of physical success. The compliance bit and the reward function read from completely separate columns, events, and tables.

R_phys grounding latency varies. Some outcomes resolve in seconds (motor amps recover, melt pressure normalizes). Others resolve in hours (lab QC on the extruded product). The event record carries a pending_R_phys deadline; if the deadline passes without R_phys arrival, the event is marked INDETERMINATE and does not update C. The deferred update queue is keyed by (event_id, telemetry_arrival_deadline) with explicit timeout handling.

### 5.4 The Withhold-Sampling Subsystem

OGC prevents the reflexivity trap at the per-event confidence-update level. A subtler version of the trap emerges at the distribution level once the system's accumulated knowledge graph begins influencing operator behavior at scale. The CIAER+ graph assigns weights to reconciliation pathways based on confirmed-Result outcomes. Once a Personal AI Twin offers advisory guidance derived from the graph, operators who follow high-weight guidance will tend to produce confirming Results, which will further reinforce the weights, which will produce more confirmations — even in regions of the operational envelope where the underlying causal model is wrong or has drifted. The graph becomes a self-confirming artifact rather than an independently validated model of the domain.

The withhold-sampling subsystem mitigates this. Once the Twin issues advisory guidance, the system maintains three distributions in parallel: P (pre-Twin expert baseline corpus, frozen reference), Q (live distribution under Twin guidance), and Q′ (withheld counterfactual distribution). At an empirically calibrated rate p_withhold (default 0.05), the Twin withholds high-weight guidance on a randomly selected subset of matched events. The operator acts from their own generative model unassisted. The resulting Cause-to-Result chains for those events enter the corpus tagged as counterfactual samples and update Q′ instead of Q.

Persistent divergence between Q and Q′ — measured as D_KL(Q ‖ Q′) — is the signal that the graph's current weights have decoupled from operational reality and that re-weighting or retraining is required. The reference distribution P remains structurally separable from Q and Q′ so that D_KL(P ‖ Q) and D_KL(P ‖ Q′) can be computed independently. The withhold coordinator is instrumented from Phase 1 with p_withhold = 0 — the data path exists before the data does, because counterfactual evidence collection cannot be retrofitted onto a deployed system without distorting the corpus.

### 5.5 Why This Matters

OGC and withhold-sampling together implement what biological evolution implements through the separation of selection pressure from organism behavior: an external grounding signal that the system cannot manufacture by its own actions. In biology, selection is unforgiving and external. In AI systems trained on observational data, selection becomes endogenous unless explicit architectural commitments prevent it. The pathology that has dogged deployed RL systems is the pathology of selection becoming endogenous (Amodei et al. 2016). OGC's foreign-key enforcement and withhold-sampling's distributional separation are the architectural commitments that keep selection exogenous in ArcShield.

---

## 6. The Documentation Architecture: Genomic Structure for Self-Extending Systems

### 6.1 The Von Neumann Triple

Von Neumann (Burks 1966) proved that any self-replicating system must contain three logically distinct components. A description (D) — an inert encoding of what the system is. A universal constructor (A) — a mechanism that reads D and builds the described system. A universal copier (B) — a mechanism that duplicates D so the next instance has it.

The crucial property is that D is separate from A. The description is read by the constructor but does not participate in construction. The description is copied verbatim by the copier without being interpreted. This separation is what makes evolution possible: mutations accumulate in D without immediately destroying the constructor, and the constructor can be improved without rewriting D.

We argue that the reflexivity problem in §5 is the failure mode of collapsing D and A. When the description gets written by the constructor, the system has no external reference to correct against. Biology solved this 3.5 billion years ago by keeping DNA chemically separate from the ribosomes that read it. ArcShield solves it architecturally by keeping R_phys (the description's grounding signal) separate from operator compliance (the constructor's behavior).

### 6.2 The Three Nested Instances

ArcShield instantiates the von Neumann triple at three nested scales. We describe each.

**Scale 1 — The Product (mp4Real at runtime).** Description: the behavioral codebook, the CIAER+ schema, the LLR thresholds, the OGC update rule. Constructor: the edge device and backend codec runtime that read the description and encode new events against it. Copier: the corpus persistence layer and codebook synchronization across deployments. The I-frame is the genome at shift start. Every P-frame is a delta against that baseline. The codebook is the accumulated library of recognized patterns — the equivalent of an organism's repertoire of gene expressions. New events that do not match a primitive cause the codebook to expand: a mutation in the description that the constructor can immediately use. The reflexivity firewall at this scale is OGC.

**Scale 2 — The Build (the codebase that produces mp4Real).** Description: CLAUDE.md, an exhaustive specification of the invariant architecture, schema, commitments, and intellectual property markings, written for consumption by any constructor — a human engineer, an AI coding agent, a future LLM. Constructor: any agent interpreting CLAUDE.md and producing code. Copier: version control plus the handoff document itself (CLAUDE.md is designed to be readable by a cold instance). CLAUDE.md is the genome at build time. Every commit is a delta against the invariant. Architectural patterns that emerge during the build cause CLAUDE.md to be updated explicitly, with author sign-off: a mutation in the description that requires intentional validation rather than drift. The reflexivity firewall at this scale is the prohibition on Claude Code unilaterally rewriting architectural commitments. The constructor cannot rewrite the description without external authorization.

**Scale 3 — The Meta (the documentation system itself).** Description: CLAUDE.md (what stays the same across sessions). Phenotype (live execution state): CURRENT_PHASE.md (what this session is actually doing). Epigenetic layer: the session log (CURRENT_PHASE.md §8 — heritable marks that survive across sessions). Selective principle layer: SELECTION_PRINCIPLE.md (the architect's externalized decision rules). Constructor: any LLM instance reading the files and producing the next session's work. Copier: the fact that all files are markdown in version control — readable by any future constructor.

The three scales are not just similar. They are homomorphic with respect to five invariants we identify in §6.4.

### 6.3 The Genotype-Phenotype-Epigenome Parallel in Biology

In biology, DNA encodes the invariant instruction set (Watson & Crick 1953). Gene expression is the phenotypic state of a cell at a given moment — which parts of DNA are active. Epigenetic markers (methylation, histone modifications) determine *how* the DNA is read in this lineage at this time, persist through cell division, and are the substrate of heritable learning that does not alter the DNA sequence (Waddington 1957; Jaenisch & Bird 2003).

The mapping to our documentation system is direct: CLAUDE.md is DNA; CURRENT_PHASE.md is gene expression; the session log is the epigenome. A fresh AI coding agent reading these three files in sequence inherits the same architectural genotype as the previous instance, sees the current phenotypic state, and reads the epigenetic marks (decisions that worked, traps that bit, drift watch items) without needing to be retrained. The system inherits across sessions instead of generations.

We claim this is not metaphor. It is convergent design under the same pressure. When a system needs to persist knowledge across discontinuous instances, maintain coherence at architectural depth, accumulate operational state without drift, reproduce without losing fidelity, and iterate without losing the plot, it arrives at a layered architecture with the same logical shape regardless of whether the substrate is nucleic acid, written language, software documentation, or layered language-model memory (cf. §6.5 below).

### 6.4 The Five Structural Invariants

The three scales are homomorphic with respect to five invariants. We enumerate them because the invariants generate design decisions: a proposal that violates any of the five at any scale is rejected automatically.

**Invariant 1 — Description / Constructor separation.** Codebook expansion requires human-in-the-loop validation (product scale); architecture changes require author sign-off (build scale); genotype does not drift session to session while phenotype does (meta scale). Wherever D and A get collapsed into one mutable surface, the system loses evolvability and gains reflexivity.

**Invariant 2 — Grounded outcome signal.** R_phys from PLC telemetry (product); acceptance criteria from CLAUDE.md §11, functional tests against external spec (build); whether the next session actually worked, measured by §2 acceptance tracker in CURRENT_PHASE.md (meta). Every scale needs an external grounding signal. When the system grades itself, the system rots.

**Invariant 3 — Counterfactual preservation.** Withhold-sampled events go to Q′, separate from Q (product); Shadow Actions in the CIAER+ schema (build / schema layer); CURRENT_PHASE.md §6 Open Decisions, where alternatives are parked rather than collapsed (meta). At every scale, the system must preserve evidence of what did *not* happen. Without counterfactuals, the system has no signal for whether its current trajectory is the right one.

**Invariant 4 — Append-only history.** Event records, once captured and validated, are immutable; corrections create new events linked to the original (product). Git history; rewriting history is forbidden on main (build). Session log in CURRENT_PHASE.md §8 is append-only; the Completed section truncates by archiving, never by deletion (meta). History is the substrate of learning. Any scale that allows silent history rewriting loses the ability to detect drift. Biology never deletes — it marks, suppresses, or expresses differently.

**Invariant 5 — Bounded mutation rate.** Codebook expansion is gated by HITL validation (product); CLAUDE.md changes require author sign-off (build); the asymmetry between rapid CURRENT_PHASE.md updates and rare CLAUDE.md changes is intentional (meta). Evolution requires variation but cannot tolerate unbounded variation. Biology mutates DNA at approximately 10⁻⁹ per base per generation. Description-layer mutation rates in our system are far below phenotype-layer mutation rates by design.

### 6.5 Parallel with Recent LLM Memory Architectures

In a parallel literature, layered-memory architectures for large language model agents have been independently proposed in the 2024–2026 period. Fofadiya and Tiwari (2026) decompose dialogue history into working, episodic, and semantic layers with adaptive retrieval gating and retention regularization to control cross-session drift. MemMachine (2026) preserves ground-truth conversational episodes at sentence-level granularity, minimizing LLM dependence for routine memory operations and preserving factual integrity. Memori (2026) and Memoria (2026) propose persistent memory architectures for autonomous LLM agents that incrementally capture user traits, preferences, and behavioral patterns as structured entities and relationships.

The convergence is informative. Three independent lineages — von Neumann's formal logic of self-replication (1948), biology's three-layer inheritance architecture (1953 onward), and the very recent layered-memory architectures for LLM agents — arrive at the same structural pattern when forced to solve the same underlying problem. We argue this is not coincidence. It is the consequence of the constraints imposed by the problem itself: any system that must persist information through replication while resisting reflexive self-confirmation must implement a separation between description and constructor, must ground its outcomes externally, must preserve counterfactual evidence, must accumulate history append-only, and must bound the mutation rate at the description layer.

### 6.6 The Author's Role

In a von Neumann self-replicating system, there is one role the triple does not fill: the role of the entity that wrote the description in the first place. The constructor reads it. The copier duplicates it. But something — or someone — had to compose it.

In biology, the answer is evolution. No author. Selection pressure over deep time, plus initial physical chemistry, plus stochasticity. The genome is the cumulative record of every ancestor that survived.

In a software project of the kind described here, the answer is the architect — the human who composes the description and authorizes its mutations. The architect is not part of the von Neumann triple. The architect performs the function that natural selection performs in biology: deciding which mutations to the description survive into the next generation, based on judgment about the physical reality the system operates in. We describe this role as the *selective principle*. It is structurally analogous to natural selection but instantiated consciously and on a compressed time scale.

The author sign-off requirement on architectural changes is not bureaucracy. It is the system's way of routing mutations through the only entity with the standing to evaluate them against external reality. The architect's relationship to the system is therefore distinct from any of the three layers within the triple. The architect is not the genome, not the phenotype, and not the epigenome. The architect is the selective principle that shapes all three.

This role has historically been ascribed to gods in theological traditions and to the watchmaker in deist arguments (Paley 1802). We do not endorse the theological framing — we treat it strictly as a structural observation. An architect of a closed self-replicating system stands in a relationship to that system that has historically only had one vocabulary. The deepest commitment of the architect is to externalize the selection function sufficiently that successors can perform it without the architect's continued presence. This is the move from architect to ancestor: the artifact carries the architect's judgment forward.

We have implemented this commitment in the SELECTION_PRINCIPLE.md file: an externalized form of the architect's tacit decision rules, captured using the same PIE elicitation protocol the system applies to industrial operators. The architect's selection function is captured by the system the architect designed. The system has become recursive.

---

## 7. Proof-of-Concept Deployment and Limitations

### 7.1 Validation Site

The proof-of-concept deployment is at PPVC Line 1, Hollowell Industries, in Helena-West Helena, Arkansas. The site produces PVC pipe via extrusion on a 32:1 L/D barrel configuration. The validation operator is the paper's author, who has substantial prior experience on the line. The system has been operational in some form since April 2026, with the live PIE demonstration of April 8, 2026 establishing the prior art priority date.

### 7.2 Current State

As of May 2026, the system has reached Phase 1 readiness: container and LLR gate are implemented in shadow mode on a Pixel 9 Pro paired with a Polar wearable for biometrics. The CIAER+ JSON schema is operational. Voice annotation capture via on-device ASR is validated. Computer vision gauge reading via Claude and Gemini Vision APIs is operational. The biometric integration layer is in baseline calibration phase, establishing personal HR, HRV-RMSSD, and nonlinear HRV baselines from the Polar PMD stream and training the accelerometer-gated activity classifier. The corpus is small and the deployment is best characterized as foundational validation of the capture pipeline rather than comprehensive empirical proof of the CIAER hypothesis.

The provisional patent has been filed under micro entity status. The arXiv submission of the technical whitepaper is gated on Paris Convention foreign-filing timing.

### 7.3 Limitations

The validation site is a single operator at a single facility in a single domain. The structural-invariance claim for CIAER+ across domains is theoretically supported by the nine-counterexample stress-test lineage (§3.6) but has not yet been demonstrated empirically on a second domain. The cross-operator transfer claim has not yet been demonstrated. The withhold-sampling subsystem has not yet been activated — the empirical calibration of p_withhold awaits Phase 4. The graph weight reflexivity argument (§5.4) is theoretical until the Twin advisory layer is live at scale.

The biometric reliability claim around the Polar transition deserves explicit qualification. Raw R-R intervals from the H10 enable defensible nonlinear HRV analysis in principle. Whether nonlinear HRV reliably discriminates cognitive decision events from physical exertion and thermal exposure in an industrial environment with 480V motors and 32:1 barrel temperatures is an open empirical question. The calibration sprint design addresses this by running the H10 and Verity Sense in parallel during initial deployment, with the H10 serving as ECG-grounded validation reference and the Verity Sense as the operational device.

Electrodermal activity is permanently unavailable on the Polar wearables; we document this honestly rather than design around it. EDA is preserved as a deferred channel behind the BiometricSource provider abstraction, with companion sensors (Emotibit, Shimmer GSR+) identified as the integration path when EDA matters for a specific downstream analysis.

Single-counterexample stress tests, however systematic, do not constitute empirical validation. The system is at a foundational stage. The principal scientific risk is not that the architecture is wrong in some subtle way but that the deployment program will not scale fast enough to falsify the architecture's predictions before alternative approaches reach equivalent capability through different routes.

---

## 8. Discussion

### 8.1 Implications for Industrial AI

The work suggests three shifts in how industrial AI systems should be designed when the goal is preserving expert judgment rather than reproducing expert behavior.

First, the *frame* matters more than the model. A system architected on the procedure-documentation frame cannot acquire causal expertise no matter how good the underlying ML is, because the training data does not contain the causal variables. The frame must be the bidirectional reconciliation frame: capture what the expert responded to, what they inferred, what they considered and rejected, what they did, what changed, and whether their model held. Schema before architecture before model.

Second, *outcome grounding is non-negotiable at the schema level*. Confidence updates that read from operator compliance are confidence updates that will eventually drift. The R_phys / compliance separation must be enforced at the database layer via foreign-key constraints, not as a code discipline. Code disciplines fail under deployment pressure; schema invariants do not.

Third, *counterfactual evidence must be designed in from the start*. The withhold-sampling subsystem instrumented at Phase 1 with p_withhold = 0 is the right architectural pattern. Once a system is deployed without the data path for counterfactuals, retrofitting it without distorting the corpus is difficult and may be impossible.

### 8.2 Implications for the Design of Self-Extending Systems

The five-file documentation architecture (README, CLAUDE.md, CURRENT_PHASE.md, ARCHITECTURE_PRINCIPLES.md, SELECTION_PRINCIPLE.md, plus ELICITATION_LOG.md) is, we believe, a useful pattern beyond the specific case of ArcShield. The pattern generalizes to any project where AI coding agents will work alongside human architects across discontinuous sessions, where architectural drift is a real risk, and where the project must outlive any particular individual's continuous attention.

The pattern's load-bearing properties are: (1) the description / constructor separation, instantiated as the prohibition on the AI agent rewriting CLAUDE.md without author sign-off; (2) the live phenotypic state file, distinct from the genome, which absorbs session-to-session change without contaminating the architecture; (3) the append-only epigenetic record, which preserves the rationale of past decisions for re-entry; (4) the externalized selection function, which makes the architect's tacit judgment inheritable; and (5) the recursive elicitation log, which captures the architect through the same protocol the system uses on its primary subjects.

We do not claim this pattern is novel in every component. Code repositories have had READMEs for decades. AI coding agents have been using project-specific context files for the better part of a year as of 2026. What we believe is novel is the *integration* of these components into a five-file structure that satisfies the five structural invariants of §6.4 jointly, and the *recursive* application of the capture protocol to the architect.

### 8.3 The Selective Principle Role

The architect's role in a system of this kind is structurally distinct from the roles within the von Neumann triple. The architect is the selective principle: the entity that performs the function of natural selection on the system's evolution, consciously and on a compressed time scale.

This role carries responsibilities. The architect must externalize the selection function sufficiently that the system can outlive their continuous involvement. The architect must resist the temptation to be the constructor — to write the code, to make the day-to-day decisions, to be in every session. The architect's leverage is in the description layer, not the constructor layer. Time spent in the constructor role is, for the architect specifically, lower-leverage than time spent making the description sufficient.

The architect must also resist the temptation to over-author. A description that captures too much constrains the constructor; a description that captures too little fails to constrain it. Biology found the right ratio over four billion years. A conscious architect must find it deliberately. SELECTION_PRINCIPLE.md is our attempt to capture *how an architect should decide* in the form of a rule set that successors can read and apply — not because the rules are universal but because the rules are this architect's, made externally inheritable.

### 8.4 What This System Has Become

We close this discussion with a structural observation. The original scope of the system was capturing PVC extrusion expertise from veteran operators. The system has become, in the course of its development, something larger: a general protocol for converting cognitive agents' tacit knowledge into transmissible structure, applicable to operators, to architects, and to AI systems, validated initially in a domain where the stakes are unforgiving.

The protocol has structural properties that align it with previous instances of the same pattern at different substrates and time scales: writing (externalized memory), DNA (heritable structure), language (transmissible structure), evolution (accumulation under selection), and infrastructure (self-extension through capturing improvers). The reason these alignments keep landing is, we believe, that the protocol shares deep structure with all of them — it is doing what they did, but consciously, on a compressed time scale, for a bounded domain, with engineering discipline.

We do not claim this is the only such protocol. We do not claim it is complete. We claim that the structural pattern is real, that its constraints are non-arbitrary, and that the convergence of formal-logical, biological, and ML-engineering lineages on the same pattern is evidence that the pattern reflects something about the problem of preserving information through replication that is independent of substrate.

---

## 9. Conclusion

ArcShield began as an engineering project to capture industrial decision-making before veteran operators retired. It has become something larger: a general protocol for transmissible expertise, with structural properties that converge with von Neumann's formal logic of self-replication, with biology's genotype-phenotype-epigenome architecture of inheritance, and with the most recent layered-memory architectures for large language model agents.

We have specified the four engineering contributions: the CIAER+ schema, the mp4Real codec, the OGC firewall with withhold-sampling, and the five-file documentation architecture. We have argued that the fifth property — the general protocol — emerges from the joint application of the four. We have documented the structural isomorphisms with biology and von Neumann self-replication, and we have argued that the convergence reflects a constraint imposed by the problem rather than a borrowing of metaphor.

We have also documented our limitations. The validation set is a sample of one. The deployment is at a foundational stage. The structural-invariance claim has theoretical support through nine-counterexample stress-testing but has not yet been empirically validated on a second domain or a second operator. The withhold-sampling subsystem is instrumented but not active. The Twin advisory layer is not yet built.

The work's most consequential claim is also its most testable: that an architecture built on the bidirectional reconciliation frame, with outcome-grounded confidence, counterfactual preservation, and architect-recursive documentation, will produce a corpus from which downstream AI systems can acquire causal expert judgment that systems trained on procedure-documentation corpora cannot. The proof-of-concept at PPVC Line 1 is the first step in testing that claim empirically. Subsequent deployments will test it under cross-operator, cross-facility, and ultimately cross-domain conditions.

If the claim survives those tests, the implications extend beyond industrial AI to any domain where tacit expertise needs to be made transmissible. We have suggested that the protocol described here is a candidate for the fourth cognitive prosthesis at civilizational scale — following writing, the printing press, and the internet — externalizing the judgment under uncertainty that has historically lived only inside experts' bodies. We close in awareness that such a claim is large and that the architect's responsibility is to discharge it through evidence rather than rhetoric.

---

## References

Amodei, D., Olah, C., Steinhardt, J., Christiano, P., Schulman, J., & Mané, D. (2016). Concrete problems in AI safety. *arXiv:1606.06565*.

Burks, A. W. (Ed.). (1966). *Theory of self-reproducing automata*, by John von Neumann. University of Illinois Press.

Damasio, A. R. (1994). *Descartes' error: Emotion, reason, and the human brain*. Putnam.

de Haan, P., Jayaraman, D., & Levine, S. (2019). Causal confusion in imitation learning. *Advances in Neural Information Processing Systems*, 32.

Ensign, D., Friedler, S. A., Neville, S., Scheidegger, C., & Venkatasubramanian, S. (2018). Runaway feedback loops in predictive policing. *Conference on Fairness, Accountability, and Transparency (FAT*)*.

Fofadiya, B., & Tiwari, A. (2026). Multi-layered memory architectures for large language model agents. *arXiv preprint*.

Hu, S., & Clune, J. (2023). Thought cloning: Learning to think while acting by imitating human thinking. *Advances in Neural Information Processing Systems*, 36.

Jaenisch, R., & Bird, A. (2003). Epigenetic regulation of gene expression: How the genome integrates intrinsic and environmental signals. *Nature Genetics*, 33(3), 245–254.

Kay, S. M. (1998). *Fundamentals of statistical signal processing, Volume II: Detection theory*. Prentice Hall.

Klein, G. (1998). *Sources of power: How people make decisions*. MIT Press.

Krakovna, V., Uesato, J., Mikulik, V., Rahtz, M., Everitt, T., Kumar, R., Kenton, Z., Leike, J., & Legg, S. (2020). Specification gaming: The flip side of AI ingenuity. *DeepMind blog*.

MemMachine. (2026). Ground-truth-preserving conversational memory architecture. *arXiv preprint*.

Memori. (2026). Persistent memory at the API layer for autonomous LLM agents. *arXiv preprint*.

Memoria. (2026). Weighted knowledge graphs for incremental user modeling. *arXiv preprint*.

O'Neil, C. (2016). *Weapons of math destruction: How big data increases inequality and threatens democracy*. Crown.

Paley, W. (1802). *Natural theology: Or, evidences of the existence and attributes of the deity, collected from the appearances of nature*. R. Faulder.

Pigliucci, M. (2010). Genotype-phenotype mapping and the end of the "genes as blueprint" metaphor. *Philosophical Transactions of the Royal Society B: Biological Sciences*, 365(1540), 557–566.

Polanyi, M. (1966). *The tacit dimension*. Doubleday.

Rasmussen, J. (1983). Skills, rules, and knowledge: Signals, signs, and symbols, and other distinctions in human performance models. *IEEE Transactions on Systems, Man, and Cybernetics*, SMC-13(3), 257–266.

Rissanen, J. (1978). Modeling by shortest data description. *Automatica*, 14(5), 465–471.

Rietveld, E., & Kiverstein, J. (2014). A rich landscape of affordances. *Ecological Psychology*, 26(4), 325–352.

Schmutz, J. B., Meier, L. L., & Manser, T. (2019). How effective is teamwork really? The relationship between teamwork and performance in healthcare teams: A systematic review and meta-analysis. *BMJ Open*, 9(9).

Waddington, C. H. (1957). *The strategy of the genes*. Allen & Unwin.

Watson, J. D., & Crick, F. H. C. (1953). Molecular structure of nucleic acids: A structure for deoxyribose nucleic acid. *Nature*, 171(4356), 737–738.

---

**Intellectual Property and Trademark Notice**

mp4Real™, ArcShield™, CIAER™, and CIAER+™ are trademarks of Capps Consulting Company LLC. The multi-track cyber-physical capture architecture, the application of log-likelihood ratio gating to multimodal industrial decision events, the outcome-grounded confidence update rule, the withhold-sampling reflexivity-detection subsystem, and the behavioral codebook discretization methods described in this paper are the proprietary intellectual property of Kahn Capps and Capps Consulting Company LLC. Unauthorized commercial use, reproduction, or implementation of the mp4Real™ container architecture or the CIAER™ and CIAER+™ schemas without explicit licensing is prohibited. All rights reserved.

*Capps Consulting Company LLC · Helena-West Helena, Arkansas · 2026*
