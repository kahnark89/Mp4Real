# Conversation: Experiential Phase Rotation & Phase-Parallel Learning
**Date:** 2026-06-05
**Participants:** Kahn Capps / Claude Code (claude-sonnet-4-6)

---

## Message 1 — Kahn

the headline thought was that the model being trained needs to learn in a phased type environment that is in constant rotation with the deployed state of said model. The AI needs to see itself learning the task by experience just like a human. only then will it have the clear picture of why not to attempt this action in this scenario. and why this option over the other option. It becomes its own causal DAG of a vast many counterfactuals and shadow actions alongside the one action trace that becomes the real world solution set.

---

## Message 2 — Claude

The insight lands with full force. Let me name it precisely and map it to what needs to change architecturally.

---

## What you're describing: Experiential Phase Rotation (EPR)

Not fine-tuning. Not RLHF. The model is a participant in its own corpus. Each deployed phase generates training signal *about the model's own deliberation*, not just about operator behavior. The model learns from watching itself be wrong, see the outcome, and update — the same causal loop a human expert runs.

The critical distinction from standard RLHF:

> Standard: human rates output → update weights → redeploy. Model never sees its own deliberation as data.
>
> EPR: model's *recommended action* + *model's shadow actions* + *model's confidence* at time T become first-class schema fields. At T+Δt, R_phys arrives. The model trains on the full trace including its own earlier deliberation and how it resolved.

---

## What the architecture already supports vs. what's missing

**Already built for this:**

- CIAER+ `shadow_actions` — operator's rejected alternatives. Structural, not free text.
- OGC firewall — R_phys is the only update path, compliance is only a gate.
- Withhold-sampling (Q′) — counterfactual traces where the model's guidance was suppressed.
- Phase-gated advancement with acceptance criteria.

**What's not explicit yet — the gap:**

The schema captures the *operator's* deliberation. It doesn't capture the *model's* deliberation as a parallel first-class structure. For EPR to work, you need:

```json
"twin_deliberation": {
  "model_version": "twin_v2.1",
  "recommended_action": { ... },       // â_t
  "model_shadow_actions": [ ... ],     // what the model considered and rejected
  "model_confidence": 0.74,
  "rationale_embedding": "...",        // the model's internal causal chain
  "withheld": false,                   // whether this was a withhold-sample event
  "model_correct": null                // filled at R_phys arrival: bool
}
```

This runs *alongside* the operator's `action` and `shadow_actions`, not replacing them. The training corpus then contains four interleaved traces per event:

1. **Operator chose** + operator rejected (shadow_actions)
2. **Model recommended** + model rejected (model_shadow_actions)
3. **Physical outcome** (R_phys)
4. **Counterfactual** (Q′ events: what happened when model was withheld)

The DAG the model is learning is the intersection of all four. Over rotation cycles, the model builds something humans can't build explicitly: a queryable causal map of "I recommended X in state S, the operator complied, R_phys was bad — and separately, I recommended Y in similar state S′, the operator was withheld, they did Z, R_phys was good. Therefore X is the wrong choice in the S family."

---

## The rotation schedule mapped to existing phases

| Phase | Model role | Training signal available |
|---|---|---|
| 1 | Shadow — no guidance issued | Operator deliberation only. Model shadow actions logged but not delivered. |
| 2 | Advisory live, p_withhold=0 | Operator compliance + R_phys. Model shadow actions vs. operator choices. |
| 3 | Advisory + withhold p=0.05 | Q′ counterfactuals enter corpus. First rotation: retrain on Phase 1+2 corpus, deploy v2. |
| 4 | Active rotation | Prior model versions' recommendations become historical shadow actions. Model trains on its own earlier mistakes with known outcomes. |

The critical implementation requirement at Phase 3: **model versions must be pinned to event records**. Every CIAER+ event needs `twin_deliberation.model_version` so that at Phase 4, the training pipeline can ask: "what did v1 recommend here, and what did R_phys say about that recommendation?"

---

## The schema addition to spec now

This needs to go into `core-schema` before Phase 2 ships, because retrofitting it onto existing events is lossy. Specifically:

`twin_deliberation` block added to CIAER+ at the same level as `action` and `shadow_actions`. Status: **DERIVED** (system-populated, never manually entered). Required when `advisory_mode = true`, null-allowed in Phase 1 shadow mode.

The `model_shadow_actions` subfield mirrors the structure of operator `shadow_actions`:

```
{ action_type, rejection_rationale, confidence_in_rejection }
```

except `rejection_rationale` here is the model's internal reasoning chain, not elicited text — so it's an embedding + natural language summary.

---

## Why this produces the causal clarity you're pointing at

A human expert accumulates ~10,000 hours of "I tried X, it went wrong, I learned not to do X in condition Y." They can't tell you why — the knowledge is in the body, the hand, the pattern-match. ArcShield with EPR makes that causal chain explicit and queryable:

```
π* (matched primitive) → model recommended A, rejected {B, C, D}
                       → operator complied
                       → R_phys = 0.2 (bad)
                       → model_correct = false
                       → model revision logged

Same primitive 4 months later →
  model recommends B (A now has negative weight in this primitive family)
  → R_phys = 0.87
  → model_correct = true
```

That trace *is* the experiential learning. The model isn't just trained on outcomes — it's trained on its own deliberation history, with outcomes attached. The shadow actions become the counterfactual comparison set for every future decision in that state family.

---

**Immediate action:** I'll open a schema change to add `twin_deliberation` to `core-schema` and wire the `model_version` pin into the event persistence path before Phase 2 acceptance criteria freeze the schema. Want me to draft that now, or is there more to the rotation timing you want to spec first?

---

## Message 3 — Kahn

can a system like this be designed to run in parallel to itself with a slight change in phase kinda like electricity is run in phases

---

## Message 4 — Claude

Yes. And the electrical analogy is more precise than it might seem — let me map it through fully.

---

## Phase-Parallel EPR (PP-EPR)

In 3-phase AC, the core properties are:

- Three instances of the same waveform, offset by T/3
- At any moment, at least one phase is near peak
- The sum across phases returns to neutral (R_phys is the neutral here)
- Phase convergence = maximum power delivery
- Phase disagreement = where the interesting boundary is

All four of those translate directly.

---

## The structural mapping

A single EPR cycle has four sub-phases:

```
Shadow → Advisory → Withhold-active → Retrain
```

Run three instances, each offset by one sub-phase:

```
Instance A:  [Shadow]          [Advisory]      [Withhold]      [Retrain] → ...
Instance B:  [Advisory]        [Withhold]      [Retrain]       [Shadow]  → ...
Instance C:  [Withhold]        [Retrain]        [Shadow]       [Advisory]→ ...
             ───────────────────────────────────────────────────────────────
             t=0               t=T/4            t=T/2           t=3T/4
```

**The critical benefit:** the system never goes dark for retraining. There is always one instance in advisory mode. This is the direct equivalent of 3-phase power's no-zero-crossing-dead-spot property — continuous torque for industrial motors, continuous guidance for operators.

Single-phase EPR has a retraining gap where either a stale model advises or no model advises. That gap is a systematic bias source: operators revert to uninformed behavior, which corrupts the P baseline. PP-EPR eliminates that gap.

---

## The neutral conductor: R_phys

In AC, the neutral is the common return path — the reference all phases resolve against. Here it's R_phys. No matter which instance issued the recommendation, no matter what phase offset it's at, the physical floor outcome is the same ground truth. Cross-instance comparability is guaranteed because all instances' recommendations are evaluated against the same telemetry.

This is why the OGC firewall (§6) isn't just a single-model safeguard — it's what makes multi-phase operation coherent. Without R_phys as the neutral, phase instances would have no shared reference and their learning would diverge.

---

## "Slight" offset — the more interesting case

You said "slight change in phase." That's different from equal thirds (120°), and it's worth treating separately.

In electrical systems, two generators running at nearly the same phase will *hunt* each other — small frequency differences produce oscillations at the beat frequency. In learning systems, instances that are close in phase will **agree on almost everything but disagree on the marginal cases**. Those disagreement points are exactly the high-value training signal:

```
Instance A (phase offset +δ):  recommends action X, confidence 0.71
Instance B (phase offset -δ):  recommends action Y, confidence 0.68
Operator follows X → R_phys = 0.84
```

That event is worth 10x a consensus event for learning. The slight-offset configuration actively **generates** the disagreement events you need at decision boundaries. It's a form of automated curriculum generation — the phase difference hunts the action space's hard cases.

The "full 3-phase" (T/3 offset) gives you operational continuity. The "slight phase" configuration gives you discriminative training signal. They serve different objectives and could both be running simultaneously on different pairs of instances.

---

## Cross-phase contamination: the one real risk

In a phase fault, current from one phase bleeds into another and disrupts the balanced system. The equivalent here:

If Instance B is advising with incorrect guidance *during Instance A's shadow phase*, Instance A's "what operators do without guidance" observation is contaminated — that's not baseline behavior, it's guided behavior under a different policy.

**Architectural firewall:**

Every CIAER+ event needs a `guidance_context` field tagging which instances were active and in what phase at the time of the event:

```json
"guidance_context": {
  "instances_advising": ["twin_B_v1.8"],
  "instances_in_shadow": ["twin_A_v2.1"],
  "instances_retraining": ["twin_C_v1.5"],
  "operator_guidance_delivered": true,
  "delivering_instance": "twin_B_v1.8"
}
```

The training pipeline then partitions events by this context. Instance A's shadow-phase observations are only treated as P-baseline samples when `instances_advising` is empty, or are tagged as "shadow observations under Instance B guidance" and treated as Q′ data — which is actually *useful*: they're counterfactual samples for Instance A's eventual advisory phase.

This is elegant: **cross-phase observations are automatically counterfactual data for the non-advising instance.** The phase structure generates Q′ for free without needing the explicit withhold-sampling mechanism to carry the full load.

---

## What changes in the schema

`twin_deliberation` becomes a multi-instance block:

```json
"twin_deliberation": {
  "instances": [
    {
      "instance_id": "twin_A_v2.1",
      "current_phase": "shadow",
      "recommended_action": { ... },
      "model_shadow_actions": [ ... ],
      "confidence": 0.74,
      "delivered": false
    },
    {
      "instance_id": "twin_B_v1.8",
      "current_phase": "advisory",
      "recommended_action": { ... },
      "model_shadow_actions": [ ... ],
      "confidence": 0.68,
      "delivered": true
    }
  ],
  "inter_instance_agreement": 0.31,   // low = at a decision boundary
  "delivered_by": "twin_B_v1.8"
}
```

`inter_instance_agreement` becomes a first-class training signal. Low agreement events get priority labeling in the HITL queue because they're on the decision boundary the system is actively trying to resolve.

---

## What you get at steady state

After several rotation cycles with N phase-parallel instances, the model's causal DAG has something a single-phase system can never build:

- The same state S evaluated by multiple model versions at different training depths
- Direct comparison of those recommendations against R_phys
- Counterfactual Q′ traces built from cross-phase shadow observations rather than requiring explicit withholding
- Confidence calibration that comes from measuring inter-instance agreement vs. R_phys outcomes — the places where instances disagreed and the outcome was decisive are where the weights sharpen fastest

The "sees itself learning" property you described in the first message becomes literal: Instance A in Phase 3 is training on events that Instance A in Phase 1 was observed on while Instance B was advising. It can see what it would have recommended then, what was actually delivered, and what R_phys said.

---

**The question I'd want to spec next:** what's the right T — the full rotation period? It's bounded below by the retraining time (can't retrain faster than you can validate) and above by the corpus staleness tolerance (don't want an instance advising on too-old weights). At PPVC Line 1 with roughly one significant event per shift, I'd estimate T in the 2–4 week range initially, shrinking as event density increases. Want to work through the T parameterization?

---

## Message 5 — Kahn

i need this entire conversation in a document verbatim so i can transfer the raw version in full

---

*End of conversation document.*

---

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
